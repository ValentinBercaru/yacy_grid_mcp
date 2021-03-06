/**
 *  GridStorage
 *  Copyright 28.1.2017 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.grid.io.assets;

import java.io.File;
import java.io.IOException;

import net.yacy.grid.mcp.Data;
import net.yacy.grid.tools.MultiProtocolURL;

public class GridStorage extends PeerStorage implements Storage<byte[]> {

    private StorageFactory<byte[]> ftp = null;
    private StorageFactory<byte[]> mcp = null;
    private boolean deleteafterread;

    public GridStorage(boolean deleteafterread, File basePath) {
        super(deleteafterread, basePath);
        this.deleteafterread = deleteafterread;
        this.ftp = null;
    }
    
    public GridStorage(boolean deleteafterread) {
        super(deleteafterread, null);
        this.deleteafterread = deleteafterread;
        this.ftp = null;
    }

    public boolean connectFTP(String host, int port, String username, String password) {
        try {
            StorageFactory<byte[]> ftp = new FTPStorageFactory(host, port, username, password, this.deleteafterread);
            ftp.getStorage().checkConnection(); // test the connection
            this.ftp = ftp;
            return true;
        } catch (IOException e) {
            Data.logger.debug("GridStorage.connectFTP/4 trying to connect to the ftp server at " + host + ":" + port + " failed");
            return false;
        }
    }
    
    public boolean connectFTP(String url) {
        try {
            MultiProtocolURL u = new MultiProtocolURL(url);
            StorageFactory<byte[]> ftp = new FTPStorageFactory(u.getHost(), u.getPort(), u.getUser(), u.getPassword(), this.deleteafterread);
            ftp.getStorage().checkConnection(); // test the connection
            this.ftp = ftp;
            return true;
        } catch (IOException e) {
            Data.logger.debug("GridStorage.connectFTP/1 trying to connect to the ftp server failed", e);
            return false;
        }
    }
    
    public boolean isFTPConnected() {
        return this.ftp != null;
    }
    
    public boolean connectMCP(String host, int port) {
        try {
            this.mcp = new MCPStorageFactory(this, host, port);
            this.mcp.getStorage().checkConnection();
            return true;
        } catch (IOException e) {
            Data.logger.debug("GridStorage.connectMCP trying to connect to a Storage over MCP at " + host + ":" + port + " failed");
            return false;
        }
    }
    
    @Override
    public StorageFactory<byte[]> store(String path, byte[] asset) throws IOException {
        if (this.ftp != null) {
            retryloop: for (int retry = 0; retry < 40; retry++) {
                try {
                    return this.ftp.getStorage().store(path, asset);
                } catch (IOException e) {
                    String cause = e.getMessage();
                    // possible causes:
                    // 421 too many connections. possible counteractions: in apacheftpd, set i.e. ftpserver.user.anonymous.maxloginnumber=200 and ftpserver.user.anonymous.maxloginperip=200
                    if (cause.indexOf("421") >= 0) {try {Thread.sleep(retry * 500);} catch (InterruptedException e1) {} continue retryloop;}
                    Data.logger.debug("GridStorage.StorageFactory trying to connect to the ftp server failed: " + cause, e);
                }
            }
        }
        if (this.mcp != null) try {
            return this.mcp.getStorage().store(path, asset);
        } catch (IOException e) {
            Data.logger.debug("GridStorage.StorageFactory trying to connect to the mcp failed: " + e.getMessage(), e);
        }
        return super.store(path, asset);
    }

    @Override
    public Asset<byte[]> load(String path) throws IOException {
        if (this.ftp != null) {
            retryloop: for (int retry = 0; retry < 40; retry++) {
                try {
                    return this.ftp.getStorage().load(path);
                } catch (IOException e) {
                    String cause = e.getMessage();
                    // possible causes:
                    // 421 too many connections. possible counteractions: in apacheftpd, set i.e. ftpserver.user.anonymous.maxloginnumber=200 and ftpserver.user.anonymous.maxloginperip=200
                    if (cause.indexOf("421") >= 0) {try {Thread.sleep(retry * 500);} catch (InterruptedException e1) {} continue retryloop;}
                    if (cause.indexOf("refused") >= 0) break retryloop; // this will not go anywhere
                    Data.logger.debug("GridStorage.load trying to connect to the ftp server failed: " + cause, e);
                }
            }
        }
        if (this.mcp != null) try {
            return this.mcp.getStorage().load(path);
        } catch (IOException e) {
            Data.logger.debug("GridStorage.load trying to connect to the mcp failed: " + e.getMessage(), e);
        }
        return super.load(path);
    }

    @Override
    public void close() {
        if (this.ftp != null) try {this.ftp.close();} catch (Throwable e) {}
        try {super.close();} catch (Throwable e) {}
    }

}
