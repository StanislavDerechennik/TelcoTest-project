CREATE SCHEMA `telcotestdatabase`;
CREATE TABLE `telcotestdatabase`.`list_of_files` (

  `filename` VARCHAR(256) NOT NULL,

  `download_time` DATETIME NOT NULL,

  PRIMARY KEY (`filename`));