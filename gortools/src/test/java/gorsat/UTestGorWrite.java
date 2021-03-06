/*
 *  BEGIN_COPYRIGHT
 *
 *  Copyright (C) 2011-2013 deCODE genetics Inc.
 *  Copyright (C) 2013-2019 WuXi NextCode Inc.
 *  All Rights Reserved.
 *
 *  GORpipe is free software: you can redistribute it and/or modify
 *  it under the terms of the AFFERO GNU General Public License as published by
 *  the Free Software Foundation.
 *
 *  GORpipe is distributed "AS-IS" AND WITHOUT ANY WARRANTY OF ANY KIND,
 *  INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 *  NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR PURPOSE. See
 *  the AFFERO GNU General Public License for the complete license terms.
 *
 *  You should have received a copy of the AFFERO GNU General Public License
 *  along with GORpipe.  If not, see <http://www.gnu.org/licenses/agpl-3.0.html>
 *
 *  END_COPYRIGHT
 */

package gorsat;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Created by hjalti on 13/06/17.
 */
public class UTestGorWrite {
    private Path tmpdir;

    @Before
    public void setupTest() throws IOException {
        tmpdir = Files.createTempDirectory("test_gor_write");
        tmpdir.toFile().deleteOnExit();
    }

    @Test
    public void testGorWriteWithMd5() throws IOException {
        Path tmpfile = tmpdir.resolve("genes_md5.gorz");
        tmpfile.toFile().deleteOnExit();
        String query = "gor ../tests/data/gor/genes.gorz | write -m " + tmpfile.toAbsolutePath().normalize().toString();

        TestUtils.runGorPipeCount(query);

        Path path = tmpdir.resolve("genes_md5.gorz.md5");
        Assert.assertTrue("Md5 file does not exist", Files.exists(path));
        path.toFile().deleteOnExit();
        String md5str = new String(Files.readAllBytes(path));
        Assert.assertEquals("Not a valid md5 string", 32, md5str.length());
    }

    @Test
    @Ignore("This test is useful for load testing and performance analysis. Does not work without code change unless hekla or another proper csa volume is mounted at /mnt/csa")
    public void testGorWriteLargeFileWithMd5() throws IOException {
        Path tmpfile = tmpdir.resolve("large_md5.gorz");
        tmpfile.toFile().deleteOnExit();
        String query = "gor /mnt/csa/env/dev/projects/installation_test_project/ref/dbsnp.gorz | write -m " + tmpfile.toAbsolutePath().normalize().toString();

        TestUtils.runGorPipeCount(query);

        Path path = tmpdir.resolve("large_md5.gorz.md5");
        Assert.assertTrue("Md5 file does not exist", Files.exists(path));
        path.toFile().deleteOnExit();
        String md5str = new String(Files.readAllBytes(path));
        Assert.assertEquals("Not a valid md5 string", 32, md5str.length());
    }

    @Test
    public void testGorWriteReadWithIdx() throws IOException {
        Path tmpfile = tmpdir.resolve("genes.gorz");
        tmpfile.toFile().deleteOnExit();
        final String tmpFilePath = tmpfile.toAbsolutePath().normalize().toString();
        TestUtils.runGorPipeCount("gor ../tests/data/gor/genes.gorz | write -i CHROM " + tmpFilePath);

        Path path = tmpdir.resolve("genes.gorz.gori");
        Assert.assertTrue( "Seek index file does not exist", Files.exists(path) );
        path.toFile().deleteOnExit();

        Assert.assertTrue("Index file for " + tmpFilePath + " is incorrect.", assertIndexFileIsCorrect(tmpFilePath));

        final int count = TestUtils.runGorPipeCount("gor -p chr22 " + tmpfile.toAbsolutePath().normalize().toString());

        Assert.assertEquals("Wrong number of lines in seekindexed gorz file", 1127, count);
        TestUtils.assertTwoGorpipeResults("Pgor on indexed gorz file returns different results than on unindexed one", "pgor "+tmpfile.toAbsolutePath().normalize().toString()+"|group chrom -count | signature -timeres 1", "pgor ../tests/data/gor/genes.gorz|group chrom -count");
    }

    @Test
    public void testGorWriteReadWithFullIdx() throws IOException {
        Path tmpfile = tmpdir.resolve("genes.gorz");
        tmpfile.toFile().deleteOnExit();
        final String tmpFilePath = tmpfile.toAbsolutePath().normalize().toString();
        TestUtils.runGorPipeCount("gor ../tests/data/gor/genes.gorz | write -i FULL " + tmpFilePath); //Create index file.

        Assert.assertTrue("Index file for " + tmpFilePath + " is incorrect.", assertIndexFileIsCorrect(tmpFilePath));

        Path path = tmpdir.resolve("genes.gorz.gori");
        Assert.assertTrue( "Seek index file does not exist", Files.exists(path) );
        path.toFile().deleteOnExit();


        final int count = TestUtils.runGorPipeCount("gor -p chr22 "+tmpfile.toAbsolutePath().normalize().toString());
        Assert.assertEquals("Wrong number of lines in seekindexed gorz file", 1127, count);
        TestUtils.assertTwoGorpipeResults("Pgor on indexed gorz file returns different results than on unindexed one", "pgor "+tmpfile.toAbsolutePath().normalize().toString()+"|group chrom -count", "pgor ../tests/data/gor/genes.gorz|group chrom -count");
    }

    @Test
    public void testGorWrite() {
        Path tmpfile = tmpdir.resolve("genes_copy.gor");
        tmpfile.toFile().deleteOnExit();
        String fullpath = tmpfile.toAbsolutePath().normalize().toString();
        TestUtils.runGorPipeCount("gor ../tests/data/gor/genes.gorz | write " + fullpath);
        TestUtils.assertTwoGorpipeResults("gor ../tests/data/gor/genes.gorz", "gor " + fullpath);
    }

    @Test
    public void testGorWriteWithNonGorEnding() throws FileNotFoundException {
        Path tmpfile = tmpdir.resolve("genes_copy.non");
        tmpfile.toFile().deleteOnExit();
        String fullpath = tmpfile.toAbsolutePath().normalize().toString();
        TestUtils.runGorPipeCount("gor ../tests/data/gor/genes.gorz | write " + fullpath);
        final String res = TestUtils.runGorPipe("gor ../tests/data/gor/genes.gorz");
        final BufferedReader br = new BufferedReader(new FileReader(tmpfile.toFile()));
        final StringBuilder sb = new StringBuilder();
        br.lines().forEach(line -> sb.append(line + "\n"));
        Assert.assertEquals(res, sb.toString());
    }

    @Test
    public void testGorWriteStandardOut() throws IOException {
        final PrintStream stdout = System.out;
        final ByteArrayOutputStream bout = new ByteArrayOutputStream(1024 * 1024);
        System.setOut(new PrintStream(bout));

        Path tmpfile = tmpdir.resolve("genes_top3.gor");
        tmpfile.toFile().deleteOnExit();
        String fullpath = tmpfile.toAbsolutePath().normalize().toString();

        String query = "gor ../tests/data/gor/genes.gorz |top 3|write " + fullpath;
        String queryResult = TestUtils.runGorPipe("gor ../tests/data/gor/genes.gorz |top 3");

        TestUtils.runGorPipeIteratorOnMain(query);

        System.setOut(stdout); // Replace with actual system out

        Assert.assertEquals("Unexpected results written to file!", FileUtils.readFileToString(new File(fullpath), "utf-8"), queryResult);
    }

    @Test
    public void testForkWrite() throws IOException {
        final Path tmpDir = Files.createTempDirectory("forkWrite");
        try {
            final String path = tmpDir.toAbsolutePath() + "/dbsnpWithForkCol_test.gor";
            createForkWriteTestFiles(path);
            TestUtils.runGorPipe("gor " + path + " | write -r -f forkcol " + tmpDir.toAbsolutePath() + "/dbsnp#{fork}_test.gor");
            TestUtils.assertTwoGorpipeResults("gor ../tests/data/gor/dbsnp_test.gor", "gor " + tmpDir.toAbsolutePath() + "/dbsnp117_test.gor");
        } finally {
            FileUtils.deleteDirectory(tmpDir.toFile());
        }
    }

    private void createForkWriteTestFiles(String path) throws IOException {
        final BufferedWriter bw = new BufferedWriter(new FileWriter(path));
        final BufferedReader br = new BufferedReader(new FileReader("../tests/data/gor/dbsnp_test.gor"));
        String line;
        boolean isHeader = true;
        int colNum = -1;
        String[] newCols = null;
        while ((line = br.readLine()) != null) {
            final String[] cols = line.split("\t");
            if (isHeader) {
                newCols = new String[cols.length + 1];
                colNum = (int) (2 + (Math.random() * (cols.length - 2)));
                System.arraycopy(cols, 0, newCols, 0, colNum);
                newCols[colNum] = "forkcol";
                System.arraycopy(cols, colNum, newCols, colNum + 1, newCols.length - colNum - 1);
                bw.write(String.join("\t", newCols) + "\n");
                isHeader = false;
            } else {
                System.arraycopy(cols, 0, newCols, 0, colNum);
                newCols[colNum] = "117";
                System.arraycopy(cols, colNum, newCols, colNum + 1, newCols.length - colNum - 1);
                bw.write(String.join("\t", newCols) + "\n");
            }
        }
        bw.close();
        br.close();
    }

    @Test
    public void testForkWriteWithTAgs() throws IOException {
        final Path tmpDir = Files.createTempDirectory("forkWriteTags");
        try {
            final String path = tmpDir.toAbsolutePath() + "/dbsnpWithForkCol_test.gor";
            createForkWriteTestFiles(path);
            Path pathFoo = tmpDir.resolve("dbsnpfoo_test.gor");
            Path pathBar = tmpDir.resolve("dbsnpbar_test.gor");
            Path path117 = tmpDir.resolve("dbsnp117_test.gor");
            TestUtils.runGorPipe("gor " + path + " | write -r -f forkcol -t 'foo,bar,117' " + tmpDir.toAbsolutePath() + "/dbsnp#{fork}_test.gor");
            Assert.assertTrue(Files.exists(pathFoo));
            Assert.assertTrue(Files.size(pathFoo) < 100);
            Assert.assertTrue(Files.exists(pathBar));
            Assert.assertTrue(Files.size(pathBar) < 100);
            Assert.assertTrue(Files.exists(path117));
            Assert.assertTrue(Files.size(path117) > 100);
        } finally {
            FileUtils.deleteDirectory(tmpDir.toFile());
        }
    }

    @Test
    public void testForkWriteWithTagsWithZeroRowInputCreatesFile() {
        final String outputPath = tmpdir.toAbsolutePath().toString();
        String query = String.format("gorrow chr1,1,1 | calc xfile 0 | top 0 | write -t '0' -f xfile -r %s/data_#{fork}.gorz", outputPath);
        TestUtils.runGorPipe(query);
        Assert.assertTrue(Files.exists(tmpdir.resolve("data_0.gorz")));
    }

    @Test
    public void testSeekToFreshGorzFile() {
        final Path tmpFile = tmpdir.resolve("dbsnp_test.gorz");
        tmpFile.toFile().deleteOnExit();
        final String path = tmpFile.toAbsolutePath().normalize().toString();
        TestUtils.runGorPipe("gor ../tests/data/gor/dbsnp_test.gor | write " + path);
        boolean success = true;
        try {
            success &= Files.lines(new File("../tests/data/gor/dbsnp_test.gor").toPath()).skip(1).allMatch(line -> {
                final String[] lineSplit = line.split("\t");
                final String pos = lineSplit[0] + ":" + lineSplit[1];
                final String tmp = TestUtils.runGorPipe("gor -p " + pos + " " + path);
                final String line2 = tmp.substring(tmp.indexOf('\n') + 1, tmp.length() - 1);
                return line.equals(line2);
            });
        } catch (Exception e) {
            success = false;
        }
        Assert.assertTrue("Could not seek to every position.", success);
    }

    static boolean assertIndexFileIsCorrect(final String filePath) throws IOException {
        final String idxFilePath = filePath + ".gori";

        final Iterator<String> indexLineStream = new BufferedReader(new FileReader(idxFilePath)).lines().iterator();
        final Iterator<String> fileLineStream = new BufferedReader(new FileReader(filePath)).lines().iterator();

        long offsetInFile = fileLineStream.next().length() + 1;

        while (indexLineStream.hasNext()) {
            final String idxLine = indexLineStream.next();
            if (idxLine.startsWith("##")) {
                continue;
            }

            int idx = idxLine.indexOf('\t');
            final String wantedChr = idxLine.substring(0, idx);
            final int wantedPos = Integer.parseInt(idxLine.substring(idx + 1, idx = idxLine.indexOf('\t', idx + 1)));
            final long wantedOffset = Long.parseLong(idxLine.substring(idx + 1));

            boolean matched = false;
            while (fileLineStream.hasNext() && !matched) {
                final String line = fileLineStream.next();
                offsetInFile += line.length() + 1;

                final String chr = line.substring(0, idx = line.indexOf('\t'));
                final int pos = Integer.parseInt(line.substring(idx + 1, line.indexOf('\t', idx + 1)));

                if (chr.compareTo(wantedChr) > 0 || (chr.equals(wantedChr) && pos > wantedPos)) {
                    break;
                } else if (chr.equals(wantedChr) && pos == wantedPos && offsetInFile == wantedOffset) {
                    matched = true;
                }
            }
            if (!matched) return false;
        }
        return true;
    }
}
