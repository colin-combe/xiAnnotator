
This is an example service file on how to enable the annotator during start up 
as independent service
this file should be placed under 
/etc/init.d 
and linked from the 
/etc/rc?.d 
accordingly

On systemd systems one needs to then enable the xiAnnotator as a systemd service 
(hope that this is correct):
systemctl enable xiAnnotator

The script assumes, that xiAnnotator was build with the maven-assembly-plugin.


Only tested on Debian.


