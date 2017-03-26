#!/bin/bash

XDG_CONFIG_HOME=~/.config_presentation xfce4-terminal --maximize --hide-menubar --disable-server -T "Crash" -e "`pwd`/create_demo_tab.sh crash" --tab -T "CrashInt" -e "`pwd`/create_demo_tab.sh crash_int" --tab -T "CrashOOM" -e "`pwd`/create_demo_tab.sh crash_oom" --tab -T "CrashComp" -e "`pwd`/create_demo_tab.sh crash_comp" --tab -T "CrashCompRedef" -e "`pwd`/create_demo_tab.sh crash_comp_redef" &

