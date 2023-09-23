#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""Android Nearby device setup."""

import datetime
import time
from typing import Mapping

from mobly import asserts
from mobly.controllers import android_device

WIFI_COUNTRYCODE_CONFIG_TIME_SEC = 3
TOGGLE_AIRPLANE_MODE_WAIT_TIME_SEC = 2
PH_FLAG_WRITE_WAIT_TIME_SEC = 3

LOG_TAGS = [
    'Nearby',
    'NearbyMessages',
    'NearbyDiscovery',
    'NearbyConnections',
    'NearbyMediums',
    'NearbySetup',
]


def set_wifi_country_code(
    ad: android_device.AndroidDevice, country_code: str
) -> None:
  """Sets Wi-Fi country code to shrink Wi-Fi 5GHz available channels.

  When you set the phone to EU or JP, the available 5GHz channels shrinks.
  Some phones, like Pixel 2, can't use Wi-Fi Direct or Hotspot on 5GHz
  in these countries. Pixel 3+ can, but only on some channels.
  Not all of them. So, test Nearby Share or Nearby Connections without
  Wi-Fi LAN to catch any bugs and make sure we don't break it later.

  Args:
    ad: AndroidDevice, Mobly Android Device.
    country_code: WiFi Country Code.
  """
  asserts.skip_if(
      not ad.is_adb_root,
      f'Skipped setting wifi country code on device "{ad.serial}" '
      'because we do not set wifi country code on unrooted phone.',
  )
  ad.log.info(f'Set Wi-Fi country code to {country_code}.')
  ad.adb.shell('cmd wifi set-wifi-enabled disabled')
  time.sleep(WIFI_COUNTRYCODE_CONFIG_TIME_SEC)
  ad.adb.shell(f'cmd wifi force-country-code enabled {country_code}')
  enable_airplane_mode(ad)
  time.sleep(WIFI_COUNTRYCODE_CONFIG_TIME_SEC)
  disable_airplane_mode(ad)
  ad.adb.shell('cmd wifi set-wifi-enabled enabled')


def enable_logs(ad: android_device.AndroidDevice) -> None:
  """Enables Nearby related logs."""
  ad.log.info('Enable Nearby loggings.')
  for tag in LOG_TAGS:
    ad.adb.shell(f'setprop log.tag.{tag} VERBOSE')


def grant_manage_external_storage_permission(
    ad: android_device.AndroidDevice, package_name: str
) -> None:
  """Grants MANAGE_EXTERNAL_STORAGE permission to Nearby snippet."""
  build_version_sdk = int(ad.build_info['build_version_sdk'])
  if (build_version_sdk < 30):
    return
  ad.log.info(
      f'Grant MANAGE_EXTERNAL_STORAGE permission on device "{ad.serial}".'
  )
  _grant_manage_external_storage_permission(ad, package_name)


def dump_gms_version(ad: android_device.AndroidDevice) -> Mapping[str, str]:
  """Dumps GMS version from dumpsys to sponge properties."""
  out = (
      ad.adb.shell(
          'dumpsys package com.google.android.gms | grep "versionCode="'
      )
      .decode('utf-8')
      .strip()
  )
  return {f'GMS core version on {ad.serial}': out}


def toggle_airplane_mode(ad: android_device.AndroidDevice) -> None:
  asserts.skip_if(
      not ad.is_adb_root,
      f'Skipped airplane mode toggling on device "{ad.serial}" '
      'because we do not do that on unrooted phone.',
  )
  ad.log.info('turn on airplane mode')
  enable_airplane_mode(ad)
  ad.log.info('turn off airplane mode')
  disable_airplane_mode(ad)


def connect_to_wifi_wlan_till_success(
    ad: android_device.AndroidDevice, wifi_ssid: str, wifi_password: str
) -> datetime.timedelta:
  """Connecting to the specified wifi WLAN."""
  ad.log.info('Start connecting to wifi WLAN')
  wifi_connect_start = datetime.datetime.now()
  if not wifi_password:
    wifi_password = None
  connect_to_wifi(ad, wifi_ssid, wifi_password)
  return datetime.datetime.now() - wifi_connect_start


def connect_to_wifi(
    ad: android_device.AndroidDevice,
    ssid: str,
    password: str | None = None,
) -> None:
  if not ad.nearby.wifiIsEnabled():
    ad.nearby.wifiEnable()
  # return until the wifi is connected.
  ad.nearby.wifiConnectSimple(ssid, password)


def _grant_manage_external_storage_permission(
    ad: android_device.AndroidDevice, package_name: str
) -> None:
  """Grants MANAGE_EXTERNAL_STORAGE permission to Nearby snippet.

  This permission will not grant automatically by '-g' option of adb install,
  you can check the all permission granted by:
  am start -a android.settings.APPLICATION_DETAILS_SETTINGS
           -d package:{YOUR_PACKAGE}

  Reference for MANAGE_EXTERNAL_STORAGE:
  https://developer.android.com/training/data-storage/manage-all-files

  This permission will reset to default "Allow access to media only" after
  reboot if you never grant "Allow management of all files" through system UI.
  The appops command and MANAGE_EXTERNAL_STORAGE only available on API 30+.

  Args:
    ad: AndroidDevice, Mobly Android Device.
    package_name: The nearbu snippet package name.
  """
  ad.adb.shell(f'appops set --uid {package_name} MANAGE_EXTERNAL_STORAGE allow')


def enable_airplane_mode(ad: android_device.AndroidDevice) -> None:
  """Enables airplane mode on the given device."""
  ad.adb.shell(['settings', 'put', 'global', 'airplane_mode_on', '1'])
  ad.adb.shell([
      'am', 'broadcast', '-a', 'android.intent.action.AIRPLANE_MODE', '--ez',
      'state', 'true'
  ])
  time.sleep(TOGGLE_AIRPLANE_MODE_WAIT_TIME_SEC)


def disable_airplane_mode(ad: android_device.AndroidDevice) -> None:
  """Disables airplane mode on the given device."""
  ad.adb.shell(['settings', 'put', 'global', 'airplane_mode_on', '0'])
  ad.adb.shell([
      'am', 'broadcast', '-a', 'android.intent.action.AIRPLANE_MODE', '--ez',
      'state', 'false'
  ])
  time.sleep(TOGGLE_AIRPLANE_MODE_WAIT_TIME_SEC)


def enable_bluetooth_multiplex(ad: android_device.AndroidDevice) -> None:
  """Enable bluetooth multiplex on the given device."""
  pname = 'com.google.android.gms.nearby'
  flag_name = 'mediums_supports_bluetooth_multiplex_socket'
  flag_type = 'boolean'
  flag_value = 'true'
  def _is_bt_multiplex_flag_enabled() -> bool:
    sql_str = (
        'sqlite3 /data/data/com.google.android.gms/databases/phenotype.db'
        ' "select name, quote(coalesce(intVal, boolVal, floatVal, stringVal,'
        ' extensionVal)) from FlagOverrides where committed=1 AND'
        f' packageName=\'{pname}\';"'
    )
    flag_result = ad.adb.shell(sql_str).decode('utf-8').strip()
    return '1' in flag_result and flag_name in flag_result

  if _is_bt_multiplex_flag_enabled():
    ad.log.info('bt multiplex flag is already enabled.')
    return
  ad.log.info('Enable bluetooth multiplex flag.')
  ad.adb.shell(
      'am broadcast -a "com.google.android.gms.phenotype.FLAG_OVERRIDE" '
      f'--es package "{pname}" --es user "*" '
      f'--esa flags "{flag_name}" '
      f'--esa types "{flag_type}" --esa values "{flag_value}" '
      'com.google.android.gms'
      )
  time.sleep(PH_FLAG_WRITE_WAIT_TIME_SEC)

  if _is_bt_multiplex_flag_enabled():
    ad.log.info('bt multiplex flag is enabled successfully.')
  else:
    ad.log.info('failed to enable the bt multiplex flag.')


def install_apk(ad: android_device.AndroidDevice, apk_path: str) -> None:
  """Installs the apk on the given device."""
  ad.adb.install(['-r', '-g', '-t', apk_path])
