/*
 *  Copyright (C) 2010-2011 VMWare, Inc. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.wavemaker.tools.project;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Hashtable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import com.wavemaker.common.WMRuntimeException;
import com.wavemaker.common.util.IOUtils;
import com.wavemaker.runtime.server.DownloadResponse;

public class ResourceManager {

	public static DownloadResponse downloadFile(File f, String filename,
			boolean isZip) throws IOException {
		DownloadResponse ret = new DownloadResponse();

		// Setup the DownloadResponse
		FileInputStream fis = new FileInputStream(f);
		ret.setContents(fis);
		ret.setContentType((isZip) ? "application/zip" : "application/unknown");
		ret.setFileName(filename);
		return ret;
	}

	public static File createZipFile(File f, File tmpDir) {

		File destFile = new File(tmpDir, f.getName() + ".zip");
		try {

			FileOutputStream dest = new FileOutputStream(destFile.toString());
			ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(
					dest));

			addToZipStream(f, out, "");

			out.close();
			return destFile;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (File) null;
	}

	public static void addToZipStream(File f, ZipOutputStream out, String path) {
		System.out.println("add to stream: " + path + "/" + f.getName());

		final int BUFFER = 2048;
		byte data[] = new byte[BUFFER];
		BufferedInputStream origin = null;

		// get a list of files from current directory
		File files[] = f.listFiles();

		for (int i = 0; i < files.length; i++) {
			if (files[i].getName().startsWith("."))
				continue;
			if (files[i].isDirectory()) {
				System.out.println("PATH:" + path + ", NAME: " + f.getName());
				addToZipStream(files[i], out, path + "/" + f.getName());
			} else {
				try {
					FileInputStream fi = new FileInputStream(
							files[i].toString());

					origin = new BufferedInputStream(fi, BUFFER);
					// ZipEntry entry = new ZipEntry(files[i].toString());
					ZipEntry entry = new ZipEntry(path + "/" + f.getName()
							+ "/" + files[i].getName());
					System.out.println("Adding: " + path + "/" + f.getName()
							+ "/" + files[i].getName());
					out.putNextEntry(entry);
					int count;
					while ((count = origin.read(data, 0, BUFFER)) != -1) {
						out.write(data, 0, count);
					}
					origin.close();
				} catch (Exception e) {
				}
			}
		}
	}

	static public String uploadFile(MultipartFile file, File tmpDir)
			throws IOException {
		DownloadResponse ret = new DownloadResponse();

		File outputFile = new File(tmpDir, file.getOriginalFilename());
		// System.out.println("writing the content of uploaded file to: "+outputFile);

		FileOutputStream fos = new FileOutputStream(outputFile);
		IOUtils.copy(file.getInputStream(), fos);
		file.getInputStream().close();
		fos.close();
		return file.getOriginalFilename();
	}

	static public Hashtable[] getListing(File curdir, File jarListFile) {
		File[] listing = curdir.listFiles(new java.io.FilenameFilter() {
			public boolean accept(File dir, String name) {
				return (name.indexOf(".") != 0);
			}
		});
		Hashtable[] myfiles = new Hashtable[listing.length];
		for (int i = 0; i < listing.length; i++) {
			Hashtable F = new Hashtable();
			String name = listing[i].getName();
			F.put("file", name);
			myfiles[i] = F;
			if (listing[i].isDirectory()) {
				F.put("type", "folder");
				F.put("files", getListing(listing[i], jarListFile));
			} else {
				F.put("type", "file");
				if (name.endsWith(".jar"))
					F.put("isInClassPath",
							isJarInClassPath(listing[i], jarListFile));
				// F.mFiles = new MyFile[0];
			}
		}
		return myfiles;
	}

	static public boolean isJarInClassPath(File resourceFile, File jarListFile) {
		if (!jarListFile.exists())
			return false;
		try {
			String[] fileList = IOUtils.read(jarListFile).split("\n");
			for (int i = 0; i < fileList.length; i++) {
				if (resourceFile.equals(new File(jarListFile.getParentFile(),
						fileList[i])))
					return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public static void ReplaceTextInFile(OutputStream outputStream, Resource file, String findText,
			String replaceText) {
		try {
			String newtext = FileCopyUtils.copyToString(new InputStreamReader(file.getInputStream()));
			newtext = newtext.replaceAll(findText, replaceText);
			FileCopyUtils.copy(newtext, new OutputStreamWriter(outputStream));
		} catch (Exception ex) {
			throw new WMRuntimeException(ex);
		}
	}

	public static void ReplaceTextInProjectFile(Project project, Resource file,
			String findText, String replaceText) {
		try {
			String newText = project.readFile(file);
			newText = newText.replaceAll(findText, replaceText);
			project.writeFile(file, newText);
		} catch (IOException ex) {
			throw new WMRuntimeException(ex);
		}
	}

	public static Resource unzipFile(StudioConfiguration studioConfiguration,
			Resource zipfile) {
		String zipname = zipfile.getFilename();
		int extindex = zipname.lastIndexOf(".");
		String folderName;
		if (extindex == -1)
			folderName = zipname + "_folder";
		else
			folderName = zipname.substring(0, extindex);

		try {
			Resource zipFolder = zipfile.createRelative(folderName + "/");
			for (int i = 0; zipFolder.exists(); i++) {
				zipFolder = zipfile.createRelative(folderName + i + "/");
			}
			studioConfiguration.prepareForWriting(zipFolder);

			InputStream fis = zipfile.getInputStream();
			ZipInputStream zis = new ZipInputStream(
					new BufferedInputStream(fis));
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.isDirectory()) {
					studioConfiguration.createPath(zipFolder, entry.getName()
							+ "/");
				} else {
					Resource outputFile = studioConfiguration.createPath(
							zipFolder, entry.getName());
					FileCopyUtils.copy(zis,
							studioConfiguration.getOutputStream(outputFile));
				}
			}
			zis.close();

			studioConfiguration.deleteFile(zipfile);
			return zipFolder;
		} catch (Exception ex) {
			throw new WMRuntimeException(ex);
		}
	}
}