#
#
#
import glob
import os
import subprocess
import sys
import time

def whereis(program):
    for path in os.environ.get('PATH', '').split(os.pathsep):
        if os.path.exists(os.path.join(path, program)) and \
           not os.path.isdir(os.path.join(path, program)):
            return os.path.join(path, program)
    return None

def startStratagus(AI=False):
    if os.name == 'posix':
        path = whereis('stratagus')
    else:
        path = whereis('stratagus.exe')
    if not path:
        raise Exception('stratagus executable not found in path.')
    path,filename = os.path.split(path)
    cmd = ['stratagus', '-g', 'scripts/gameloop.lua']
    if not AI:
        cmd.append('-a')
        cmd.append('0')
    proc = subprocess.Popen(cmd, cwd=path)
    print "launched stratagus pid=", proc.pid, "cmd=", cmd
    return proc

################################################################################
# main
################################################################################

# clean up a little.
#for fn in glob.glob('*.txt'):
#    os.remove(fn)

if len(sys.argv) > 1:
    configsfile = open(sys.argv[1],'r')
    configs = ['configs/' + l.rstrip() for l in configsfile.readlines()]
    configsfile.close()
else:
    configs = glob.glob('configs/*.yaml')


start = time.time()

file = open('run.txt', 'w')
i = 0
for name in configs:
    AI = 'builtin' in name
    proc = startStratagus(AI)
    if os.name == 'posix':
        cmd = './client.sh -c {0} -v 0 -m 3'.format(name)
    else:
        cmd = 'client -c {0} -v 0 -m 3'.format(name)
        #cmd = 'client -c {0}'.format(name)

    print cmd
    for line in os.popen(cmd):
        file.write(line)
    proc.kill()
    i += 1
    print "Pair ", i, "of", len(configs), "done."

file.close()

end = time.time()
elapsed = end - start

print "Evaluation took", int(elapsed/60), "minutes", int(elapsed % 60), "seconds."