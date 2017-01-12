# TFTPshizz
TFTP Server 
Author: Austin Pontén & Daniel Hammerin

DELETE FROM Classes
WHERE class IN
(SELECT class
 FROM Ships
 GROUP BY class
 HAVING COUNT(name) < 3);
