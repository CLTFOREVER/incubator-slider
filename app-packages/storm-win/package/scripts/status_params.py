#!/usr/bin/env python
"""
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

"""
from resource_management import *

config = Script.get_config()

container_id = config['configurations']['global']['app_container_id']
pid_dir = config['configurations']['global']['app_pid_dir']
pid_nimbus = format("{pid_dir}/nimbus.pid")
pid_supervisor = format("{pid_dir}/supervisor.pid")
pid_drpc = format("{pid_dir}/drpc.pid")
pid_ui = format("{pid_dir}/ui.pid")
pid_logviewer = format("{pid_dir}/logviewer.pid")
pid_files = {"logviewer":pid_logviewer,
             "ui": pid_ui,
             "nimbus": pid_nimbus,
             "supervisor": pid_supervisor,
             "drpc": pid_drpc}