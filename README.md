# TFTP Server In Java
This is a TFTP server implementation in Java.

## Authors 
Austin Pontén & Daniel Hammerin

## temp
DELETE FROM Classes
WHERE class IN
(SELECT class
 FROM Ships
 GROUP BY class
 HAVING COUNT(name) < 3);
