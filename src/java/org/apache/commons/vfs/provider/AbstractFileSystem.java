/*
 * Copyright 2002, 2003,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.vfs.provider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs.Capability;
import org.apache.commons.vfs.FileChangeEvent;
import org.apache.commons.vfs.FileListener;
import org.apache.commons.vfs.FileName;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSelector;
import org.apache.commons.vfs.FileSystem;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.commons.vfs.FileSystemOptions;
import org.apache.commons.vfs.FilesCache;
import org.apache.commons.vfs.VfsLog;
import org.apache.commons.vfs.util.Messages;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * A partial {@link org.apache.commons.vfs.FileSystem} implementation.
 *
 * @author <a href="mailto:adammurdoch@apache.org">Adam Murdoch</a>
 * @version $Revision: 1.2 $ $Date: 2002/04/07 02:27:56 $
 */
public abstract class AbstractFileSystem
    extends AbstractVfsComponent
    implements FileSystem
{
    private final static Log log = LogFactory.getLog(AbstractFileSystem.class);

    private final FileName rootName;
    private FileObject parentLayer;
    // private FileObject root;
    private final Collection caps = new HashSet();

    /**
     * Map from FileName to FileObject.
     */
    private FilesCache files;

    /**
     * Map from FileName to an ArrayList of listeners for that file.
     */
    private final Map listenerMap = new HashMap();

    /**
     * FileSystemOptions used for configuration
     */
    private final FileSystemOptions fileSystemOptions;

    /**
     * How many files are activley using the filesystem
     */
    private long useCount;

    protected AbstractFileSystem(final FileName rootName,
                                 final FileObject parentLayer,
                                 final FileSystemOptions fileSystemOptions)
    {
        // this.parentLayer = parentLayer;
        this.parentLayer = parentLayer;
        this.rootName = rootName;
        this.fileSystemOptions = fileSystemOptions;

        this.files = null;
    }

    /**
     * Initialises this component.
     */
    public void init() throws FileSystemException
    {
        addCapabilities(caps);
    }

    /**
     * Closes this component.
     */
    public void close()
    {
        closeCommunicationLink();
    }

    /**
     * Close the underlaying link used to access the files
     */
    protected void closeCommunicationLink()
    {
        synchronized (this)
        {
            doCloseCommunicationLink();
        }
    }

    /**
     * Close the underlaying link used to access the files
     */
    protected void doCloseCommunicationLink()
    {
    }

    /**
     * Creates a file object.  This method is called only if the requested
     * file is not cached.
     */
    protected abstract FileObject createFile(final FileName name)
        throws Exception;

    /**
     * Adds the capabilities of this file system.
     */
    protected abstract void addCapabilities(Collection caps);

    /**
     * Returns the name of the root of this file system.
     */
    protected FileName getRootName()
    {
        return rootName;
    }

    /**
     * Adds a file object to the cache.
     */
    protected void putFileToCache(final FileObject file)
    {
        getCache().putFile(file);
        // files.put(file.getName(), file);
    }

    private FilesCache getCache()
    {
        if (this.files == null)
        {
            this.files = getContext().getFileSystemManager().getFilesCache();
            if (this.files == null)
            {
                throw new RuntimeException(Messages.getString("vfs.provider/files-cache-missing.error"));
            }
        }

        return this.files;
    }

    /**
     * Returns a cached file.
     */
    protected FileObject getFileFromCache(final FileName name)
    {
        return getCache().getFile(this, name);
        // return (FileObject) files.get(name);
    }

    /**
     * remove a cached file.
     */
    protected void removeFileFromCache(final FileName name)
    {
        getCache().removeFile(this, name);
    }

    /**
     * Determines if this file system has a particular capability.
     */
    public boolean hasCapability(final Capability capability)
    {
        return caps.contains(capability);
    }

    /**
     * Retrieves the attribute with the specified name. The default
     * implementation simply throws an exception.
     */
    public Object getAttribute(final String attrName) throws FileSystemException
    {
        throw new FileSystemException("vfs.provider/get-attribute-not-supported.error");
    }

    /**
     * Sets the attribute with the specified name. The default
     * implementation simply throws an exception.
     */
    public void setAttribute(final String attrName, final Object value)
        throws FileSystemException
    {
        throw new FileSystemException("vfs.provider/set-attribute-not-supported.error");
    }

    /**
     * Returns the parent layer if this is a layered file system.
     */
    public FileObject getParentLayer() throws FileSystemException
    {
        return parentLayer;
    }

    /**
     * Returns the root file of this file system.
     */
    public FileObject getRoot() throws FileSystemException
    {
        return resolveFile(rootName);
        /*
        if (root == null)
        {
            root = resolveFile(rootName);
        }
        return root;
        */
    }

    /**
     * Finds a file in this file system.
     */
    public FileObject resolveFile(final String nameStr) throws FileSystemException
    {
        // Resolve the name, and create the file
        final FileName name = rootName.resolveName(nameStr);
        return resolveFile(name);
    }

    /**
     * Finds a file in this file system.
     */
    public FileObject resolveFile(final FileName name) throws FileSystemException
    {
        if (!rootName.getRootURI().equals(name.getRootURI()))
        {
            throw new FileSystemException("vfs.provider/mismatched-fs-for-name.error", new Object[]{name, rootName});
        }

        // imario@apache.org ==> use getFileFromCache
        FileObject file = getFileFromCache(name);
        // FileObject file = (FileObject) files.get(name);
        if (file == null)
        {
            try
            {
                synchronized (this)
                {
                    file = createFile(name);
                }
            }
            catch (Exception e)
            {
                throw new FileSystemException("vfs.provider/create-file.error", name);
            }

            // imario@apache.org ==> use putFileToCache
            putFileToCache(file);
            // files.put(name, file);
        }
        return file;
    }

    /**
     * Creates a temporary local copy of a file and its descendents.
     */
    public File replicateFile(final FileObject file,
                              final FileSelector selector)
        throws FileSystemException
    {
        if (!file.exists())
        {
            throw new FileSystemException("vfs.provider/replicate-missing-file.error", file.getName());
        }

        try
        {
            return doReplicateFile(file, selector);
        }
        catch (final Exception e)
        {
            throw new FileSystemException("vfs.provider/replicate-file.error", file.getName(), e);
        }
    }

    /**
     * Return the FileSystemOptions used to instantiate this filesystem
     */
    public FileSystemOptions getFileSystemOptions()
    {
        return fileSystemOptions;
    }

    /**
     * Return the FileSystemManager used to instantiate this filesystem
     */
    public FileSystemManager getFileSystemManager()
    {
        return getContext().getFileSystemManager();
        // return manager;
    }

    /**
     * Returns the accuracy of the last modification time
     *
     * @return ms 0 perfectly accurate, >0 might be off by this value e.g. sftp 1000ms
     */
    public double getLastModTimeAccuracy()
    {
        return 0;
    }

    /**
     * Creates a temporary local copy of a file and its descendents.
     */
    protected File doReplicateFile(final FileObject file,
                                   final FileSelector selector)
        throws Exception
    {
        return getContext().getReplicator().replicateFile(file, selector);
    }

    /**
     * Adds a junction to this file system.
     */
    public void addJunction(final String junctionPoint,
                            final FileObject targetFile)
        throws FileSystemException
    {
        throw new FileSystemException("vfs.provider/junctions-not-supported.error", rootName);
    }

    /**
     * Removes a junction from this file system.
     */
    public void removeJunction(final String junctionPoint) throws FileSystemException
    {
        throw new FileSystemException("vfs.provider/junctions-not-supported.error", rootName);
    }

    /**
     * Adds a listener on a file in this file system.
     */
    public void addListener(final FileObject file,
                            final FileListener listener)
    {
        ArrayList listeners = (ArrayList) listenerMap.get(file.getName());
        if (listeners == null)
        {
            listeners = new ArrayList();
            listenerMap.put(file.getName(), listeners);
        }
        listeners.add(listener);
    }

    /**
     * Removes a listener from a file in this file system.
     */
    public void removeListener(final FileObject file,
                               final FileListener listener)
    {
        final ArrayList listeners = (ArrayList) listenerMap.get(file.getName());
        if (listeners != null)
        {
            listeners.remove(listener);
        }
    }

    /**
     * Fires a file create event.
     */
    protected void fireFileCreated(final FileObject file)
    {
        fireEvent(new CreateEvent(file));
    }

    /**
     * Fires a file delete event.
     */
    protected void fireFileDeleted(final FileObject file)
    {
        fireEvent(new DeleteEvent(file));
    }

    /**
     * returns true if no file is using this filesystem
     */
    public boolean isReleaseable()
    {
        return useCount < 1;
    }

    void freeResources()
    {

    }

    /**
     * Fires an event.
     */
    private void fireEvent(final ChangeEvent event)
    {
        final FileObject file = event.getFile();
        final ArrayList listeners = (ArrayList) listenerMap.get(file.getName());
        if (listeners != null)
        {
            final int count = listeners.size();
            for (int i = 0; i < count; i++)
            {
                final FileListener listener = (FileListener) listeners.get(i);
                try
                {
                    event.notify(listener);
                }
                catch (final Exception e)
                {
                    final String message = Messages.getString("vfs.provider/notify-listener.warn", file);
                    // getLogger().warn(message, e);
                    VfsLog.warn(getLogger(), log, message, e);
                }
            }
        }
    }

    void fileDetached(FileObject fileObject)
    {
        useCount--;
    }

    void fileAttached(FileObject fileObject)
    {
        useCount++;

    }

    /**
     * A change event that knows how to notify a listener.
     */
    private abstract static class ChangeEvent extends FileChangeEvent
    {
        public ChangeEvent(final FileObject file)
        {
            super(file);
        }

        public abstract void notify(final FileListener listener) throws Exception;
    }

    /**
     * File creation event.
     */
    private static class CreateEvent extends ChangeEvent
    {
        public CreateEvent(final FileObject file)
        {
            super(file);
        }

        public void notify(final FileListener listener) throws Exception
        {
            listener.fileCreated(this);
        }
    }

    /**
     * File deletion event.
     */
    private static class DeleteEvent extends ChangeEvent
    {
        public DeleteEvent(final FileObject file)
        {
            super(file);
        }

        public void notify(final FileListener listener) throws Exception
        {
            listener.fileDeleted(this);
        }
    }
}
