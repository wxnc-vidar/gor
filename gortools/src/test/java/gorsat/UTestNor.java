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

import org.gorpipe.model.genome.files.gor.Row;
import org.gorpipe.model.gor.iterators.RowSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;

/**
 * Created by sigmar on 25/06/15.
 */
public class UTestNor {
    /**
     * Test noring a filesystem
     *
     */
    @Test
    public void testNorFileSystem() {
        String curdir = new File(".").getAbsolutePath();
        String query = "nor " + curdir.substring(0, curdir.length() - 1) + "src/test/java/gorsat/";

        try (RowSource iterator = TestUtils.runGorPipeIterator(query)) {
            int count = 0;
            while (iterator.hasNext()) {
                String next = iterator.next().toString();
                if (next.contains("UTestNor.java")) count++;
            }
            Assert.assertTrue("UTestNor.java not listed in nor directory output", count > 0);
        }
    }

    @Test
    public void testNorWithEmptyLines() throws IOException {
        File tempFile = File.createTempFile("norrows", "txt");
        tempFile.deleteOnExit();
        FileUtils.writeStringToFile(tempFile, "#foo\tbar\n\nfoo1\tbar1\n\n\n\nfoo3\tbar3", (Charset)null);

        int count1 = TestUtils.runGorPipeCount(String.format("nor %s", tempFile));
        int count2 = TestUtils.runGorPipeCount(String.format("nor %s -i", tempFile));

        Assert.assertTrue(count1 != count2);
        Assert.assertEquals("We should receive all 6 lines", 6, count1);
        Assert.assertEquals( "We should not get the empty line here, only 2 lines", 2, count2);
    }

    @Test
    public void testNorWithEmptyLinesInNestedQuery() throws IOException {
        File tempFile = File.createTempFile("norrows_nested", "txt");
        tempFile.deleteOnExit();
        FileUtils.writeStringToFile(tempFile, "#foo\tbar\n\nfoo1\tbar1\n\n\n\nfoo3\tbar3", (Charset)null);

        int count1 = TestUtils.runGorPipeCount(String.format("nor <(nor %s)", tempFile));
        int count2 = TestUtils.runGorPipeCount(String.format("nor <(nor %s -i)", tempFile));

        Assert.assertTrue(count1 != count2);
        Assert.assertEquals("We should receive all 6 lines", 6, count1);
        Assert.assertEquals( "We should not get the empty line here, only 2 lines", 2, count2);
    }

    @Test
    public void testAutoNorOutput() {
        String query = "nor ../tests/data/external/samtools/noheader.vcf | top 1";

        try (RowSource iterator = TestUtils.runGorPipeIterator(query)) {
            int count = 0;
            while (iterator.hasNext()) {
                String next = iterator.next().toString();
                if (next.startsWith("chrN")) count++;
            }
            Assert.assertTrue("chrN not present in output", count > 0);
        }
    }

    @Test
    public void testDepthRangeNoringFolderTopLevel() {
        String query = "nor ../tests";

        try (RowSource iterator = TestUtils.runGorPipeIterator(query)) {
            int depthRange = getDepthRangeFromIterator(iterator);
            Assert.assertEquals("Depth should be 1 when only scanning top level folder.", 1, depthRange);
        }
    }

    @Test
    public void testDepthRangeNoringFolderUnlimited() {
        String query = "nor ../tests -r";

        try (RowSource iterator = TestUtils.runGorPipeIterator(query)) {
            int depthRange = getDepthRangeFromIterator(iterator);
            Assert.assertTrue("Depth should be greater than 3 when scanning with no limit.", depthRange > 3);
        }
    }

    @Test
    public void testDepthRangeNoringFolderLimit2() {
        String query = "nor ../tests -r -d 2";

        try (RowSource iterator = TestUtils.runGorPipeIterator(query)) {
            int depthRange = getDepthRangeFromIterator(iterator);
            Assert.assertEquals("Depth should be 2 when scanning with -d 2 limit.", 2, depthRange);
        }
    }

    @Test
    public void testNoringFolderWithModificationDate() {
        String query = "nor ../tests -m | top 0";

        try (RowSource iterator = TestUtils.runGorPipeIterator(query)) {
            Assert.assertTrue("Header should contain the modification date.", iterator.getHeader().contains("Modified"));
        }
    }


    private int getDepthRangeFromIterator(RowSource iterator) {
        int minDepth = Integer.MAX_VALUE;
        int maxDepth = Integer.MIN_VALUE;
        while (iterator.hasNext()) {
            Row row = iterator.next();
            int depth = row.colAsInt(7);

            if (depth < minDepth)
                minDepth = depth;

            if (depth > maxDepth)
                maxDepth = depth;
        }

        if (minDepth == Integer.MAX_VALUE || minDepth == Integer.MIN_VALUE) return -1;

        return maxDepth - minDepth;
    }

    @Test
    public void testNorLongRowsFewColumns() throws IOException {
        int[] lineSizes = {1, 16, 64, 128};
        for (int sz: lineSizes) {
            int length = sz * 1000;
            String filePath = createTestFileLongLinesFewColumns(length);

            String[] result = TestUtils.runGorPipeLines("nor " + filePath + " | top 1 ");
            Assert.assertEquals(2, result.length);

            int lineLength = result[1].length();
            Assert.assertTrue(lineLength <= length + 7); // Account for chrN\t0\t
        }
    }

    @Test
    public void testNorLongRowsManyColumns() throws IOException {
        int[] lineSizes = {1, 16, 64, 128};
        for (int sz: lineSizes) {
            int length = sz * 1000;
            String filePath = createTestFileLongLinesManyColumns(length);

            String[] result = TestUtils.runGorPipeLines("nor " + filePath + " | top 1 ");
            Assert.assertEquals(2, result.length);

            int lineLength = result[1].length();
            Assert.assertTrue(lineLength <= length + 7); // Account for chrN\t0\t
        }
    }

    private String createTestFileLongLinesFewColumns(int length) throws IOException {
        File tempFile = File.createTempFile("testNorLongRowsFewColumns", ".tsv");
        tempFile.deleteOnExit();

        PrintWriter outputWriter = new PrintWriter(tempFile);
        outputWriter.println("#Col1\tCol2\tCol3");

        int columnLength = (length-3) / 3;
        String col1 = StringUtils.repeat("a", columnLength);
        String col2 = StringUtils.repeat("b", columnLength);
        String col3 = StringUtils.repeat("c", columnLength);

        for (int i = 0; i < 10; i++) {
            outputWriter.print(col1);
            outputWriter.print("\t");
            outputWriter.print(col2);
            outputWriter.print("\t");
            outputWriter.println(col3);
        }

        outputWriter.close();

        return tempFile.getAbsolutePath();
    }

    private String createTestFileLongLinesManyColumns(int length) throws IOException {
        File tempFile = File.createTempFile("testNorLongRowsManyColumns", ".tsv");
        tempFile.deleteOnExit();

        PrintWriter outputWriter = new PrintWriter(tempFile);

        outputWriter.print("#");

        int numColumns = length / 3;
        for (int column = 0; column < numColumns; column++) {
            outputWriter.print(String.format("Col%d", column));
            if (column < numColumns - 1) {
                outputWriter.print("\t");
            }
        }
        outputWriter.println();


        for (int i = 0; i < 10; i++) {
            for (int column = 0; column < numColumns - 1; column++) {
                outputWriter.print("xx");
                outputWriter.print("\t");
            }
            outputWriter.println("xx");
        }

        outputWriter.close();

        return tempFile.getAbsolutePath();
    }
}
