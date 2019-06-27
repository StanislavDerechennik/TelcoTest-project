package com.derechennik.stanislav;

import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

import com.jcraft.jsch.*;


public class TelcoTest {

    static ChannelSftp channelSftp = null;
    static Session session = null;
    static Channel channel = null;
    static String PATHSEPARATOR = "/";

    public static void main(String[] args) {
        if (args.length == 1) {
            String SFTPHOST = null; // variable for SFTP Host Name or SFTP Host IP Address
            int SFTPPORT = 0; // variable for SFTP Port Number
            String SFTPUSER = null; // variable for User Name
            String SFTPPASS = null; // variable for Password
            String SFTPWORKINGDIR = null; // variable for Source Directory on SFTP server
            String LOCALDIRECTORY = null; // variable for Local Target Directory
            String SQLUSER = null; // variable for SQL User Name
            String SQLPASS = null; // variable for SQL Password
            String SQLDATABASE = null; // variable for SQL Database Name
            String SQLTABLENAME = null; // variable for SQL Table Name

            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(new File(args[0])));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.substring(0, line.indexOf('=')).equals("sftp_host")) {
                        SFTPHOST = line.substring(line.indexOf('=') + 1);
                    }
                    if (line.substring(0, line.indexOf('=')).equals("sftp_port")) {
                        SFTPPORT = Integer.parseInt(line.substring(line.indexOf('=') + 1));
                    }
                    if (line.substring(0, line.indexOf('=')).equals("sftp_user")) {
                        SFTPUSER = line.substring(line.indexOf('=') + 1);
                    }
                    if (line.substring(0, line.indexOf('=')).equals("sftp_password")) {
                        SFTPPASS = line.substring(line.indexOf('=') + 1);
                    }
                    if (line.substring(0, line.indexOf('=')).equals("sftp_remote_dir")) {
                        SFTPWORKINGDIR = line.substring(line.indexOf('=') + 1);
                    }
                    if (line.substring(0, line.indexOf('=')).equals("local_dir")) {
                        LOCALDIRECTORY = line.substring(line.indexOf('=') + 1);
                    }
                    if (line.substring(0, line.indexOf('=')).equals("sql_user")) {
                        SQLUSER = line.substring(line.indexOf('=') + 1);
                    }
                    if (line.substring(0, line.indexOf('=')).equals("sql_password")) {
                        SQLPASS = line.substring(line.indexOf('=') + 1);
                    }
                    if (line.substring(0, line.indexOf('=')).equals("sql_database")) {
                        SQLDATABASE = line.substring(line.indexOf('=') + 1);
                    }
                    if (line.substring(0, line.indexOf('=')).equals("sql_table")) {
                        SQLTABLENAME = line.substring(line.indexOf('=') + 1);
                    }

                }

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); // pattern for date

                System.out.println("Connecting to local database...");
                String driver = "com.mysql.cj.jdbc.Driver";
                String url = "jdbc:mysql://localhost:3306/" + SQLDATABASE + "?useUnicode=true&serverTimezone=UTC&useSSL=true&verifyServerCertificate=false";
                Class.forName(driver);
                Connection conn = DriverManager.getConnection(url, SQLUSER, SQLPASS);
                System.out.println("Successfully connected to database...");

                System.out.println("Connecting to SFTP server...");
                JSch jsch = new JSch();
                session = jsch.getSession(SFTPUSER, SFTPHOST, SFTPPORT);
                session.setPassword(SFTPPASS);
                java.util.Properties config = new java.util.Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                session.connect(); // Create SFTP Session
                channel = session.openChannel("sftp"); // Open SFTP Channel
                channel.connect();
                channelSftp = (ChannelSftp) channel;
                channelSftp.cd(SFTPWORKINGDIR); // Change Directory on SFTP Server
                System.out.println("Successfully connected to SFTP server...");

                System.out.println("Downloading files...");
                recursiveFolderDownload(SFTPWORKINGDIR, LOCALDIRECTORY, conn, SQLTABLENAME, dateFormat); // Recursive folder content download from SFTP server
                System.out.println("Successfully downloaded.");

                Statement statement = conn.createStatement(); // create a Statement from the connection
                ResultSet resultSet = statement.executeQuery("SELECT * FROM " + SQLTABLENAME);
                while(resultSet.next()){
                    String fileName  = resultSet.getString(1);
                    String downloadTime = resultSet.getString(2);
                    System.out.print("filename: " + fileName);
                    System.out.println("\tdownload time: " + downloadTime);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (JSchException e) {
                e.printStackTrace();
            } catch (SftpException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (reader != null)
                        reader.close();
                    if (channelSftp != null)
                        channelSftp.disconnect();
                    if (channel != null)
                        channel.disconnect();
                    if (session != null)
                        session.disconnect();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        else
            System.out.println("Couldn't execute program. Please, start this program with only ONE parameter (path to settings file).");
    }

    private static void recursiveFolderDownload(String sourcePath, String destinationPath, Connection conn, String tableName, SimpleDateFormat dateFormat) throws SftpException, SQLException {
        Vector<ChannelSftp.LsEntry> fileAndFolderList = channelSftp.ls(sourcePath); // Let list of folder content

        //Iterate through list of folder content
        for (ChannelSftp.LsEntry item : fileAndFolderList) {

            if (!item.getAttrs().isDir()) { // Check if it is a file (not a directory).
                if (!(new File(destinationPath + PATHSEPARATOR + item.getFilename())).exists()
                        || (item.getAttrs().getMTime() > Long
                        .valueOf(new File(destinationPath + PATHSEPARATOR + item.getFilename()).lastModified()
                                / (long) 1000)
                        .intValue())) { // Download only if changed later.

                    new File(destinationPath + PATHSEPARATOR + item.getFilename());
                    channelSftp.get(sourcePath + PATHSEPARATOR + item.getFilename(),
                            destinationPath + PATHSEPARATOR + item.getFilename()); // Download file from source (source filename, destination filename).
                    Statement statement = conn.createStatement(); // create a Statement from the connection
                    String currentDate = dateFormat.format(new Date());
                    statement.executeUpdate("INSERT INTO " + tableName + " VALUES ('"+item.getFilename()+"', '"+currentDate+"')" + "ON DUPLICATE KEY UPDATE download_time='"+currentDate+"'");
                }
            } else if (!(".".equals(item.getFilename()) || "..".equals(item.getFilename()))) {
                new File(destinationPath + PATHSEPARATOR + item.getFilename()).mkdirs(); // Empty folder copy.
                recursiveFolderDownload(sourcePath + PATHSEPARATOR + item.getFilename(),
                        destinationPath + PATHSEPARATOR + item.getFilename(), conn, tableName, dateFormat); // Enter found folder on server to read its contents and create locally.
            }
        }
    }
}
