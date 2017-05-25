
export PS1="\[\e]0;\w\a\]\n\[\e[32m\]\u@\h \[\e[33m\]\w\[\e[0m\]\n\$ "

CWD=`pwd`

#export PATH=/share/output-jdk9-hs-comp-dbg/images/jdk/bin:$PATH
export PATH=/share/output-jdk9-dev-dbg/images/jdk/bin:$PATH

if [ "$1" == "crash" ]; then
export CLASSPATH=$CWD/bin
else
if [ "$1" == "crash_int" ]; then
export CLASSPATH=$CWD/bin
else
if [ "$1" == "crash_oom" ]; then
export CLASSPATH=$CWD/bin
else
if [ "$1" == "crash_comp" ]; then
export CLASSPATH=$CWD/bin
else
if [ "$1" == "crash_comp_redef" ]; then
export CLASSPATH=$CWD/bin
else
if [ "$1" == "null_check_bench" ]; then
export _JAVA_OPTIONS='-Xbatch -XX:+UseSerialGC -XX:-UseOnStackReplacement -XX:-TieredCompilation -XX:CICompilerCount=1 -XX:LoopUnrollLimit=0 -XX:-LogVMOutput'
else
if [ "$1" == "arraybounds_check" ]; then
export _JAVA_OPTIONS='-Xbatch -XX:-UseCompressedOops -XX:+UseSerialGC -XX:-UseOnStackReplacement -XX:-TieredCompilation -XX:CICompilerCount=1 -XX:LoopUnrollLimit=0 -XX:CompileCommand="quiet" -XX:-LogVMOutput'
fi
fi
fi
fi
fi
fi
fi

export LD_LIBRARY_PATH=/share/OpenJDK/hsdis
ulimit -c unlimited

#alias javac=/share/output-jdk9-hs-comp-opt/images/jdk/bin/javac
alias javac=/share/software/Java/jdk-9-b154/bin/javac
alias la='ls -la'

rm -rf /tmp/JEE2017_$1
mkdir -p /tmp/JEE2017_$1
cd /tmp/JEE2017_$1

set -o history
unset HISTFILE
history -c
history -r $CWD/.history_$1
