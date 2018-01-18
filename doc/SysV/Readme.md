
# System V init script

This is an example service file on how to enable the annotator during start up 
as independent service
this file should be placed under 

/etc/init.d 

made executable:

```
chmod u+x /etc/init.d/xiAnnotator
```

and linked from the 

/etc/rc?.d 

accordingly. E.g.:
```
cd /etc/rc2.d
ln -s ../init.d/xiAnnotator S99xiAnnotator
cd /etc/rc3.d
ln -s ../init.d/xiAnnotator S99xiAnnotator
cd /etc/rc4.d
ln -s ../init.d/xiAnnotator S99xiAnnotator
cd /etc/rc5.d
ln -s ../init.d/xiAnnotator S99xiAnnotator
cd /etc/rc0.d
ln -s ../init.d/xiAnnotator K01xiAnnotator
cd /etc/rc1.d
ln -s ../init.d/xiAnnotator K01xiAnnotator
cd /etc/rc6.d
ln -s ../init.d/xiAnnotator K01xiAnnotator
```
per default it will start the annotator on the current as:
```
http://0.0.0.0:8083/xiAnnotator/
```
Thsi can be changed e.g. to 
```
http://127.0.0.1:8083/xiAnnotator/
```
to allow only local connections or to change the port to something different.


Additionally one can use apache as a reverse proxy like:
```
<VirtualHost *:80>
...
        ProxyPass        /xiAnnotator http://127.0.0.1:8083/xiAnnotator
        ProxyPassReverse /xiAnnotator http://127.0.0.1:8083/xiAnnotator
...
</VirtualHost>

```



On systemd systems one needs to then enable the xiAnnotator as a systemd service if a sysv-wrapper exist, probably the only thing needed is to enable the service (hope that this is correct):
```
systemctl daemon-reload
systemctl enable xiAnnotator
```

The script assumes, that xiAnnotator was build with the maven-assembly-plugin and is placed under 
```
/usr/local/xiAnnotator/xiAnnotator.jar
```


Only tested on Debian, XUbuntu.


