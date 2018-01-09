package amannaly;

import com.google.common.primitives.Longs;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Arjun Mannaly
 */
public class HaloDBFileMergeTest extends TestBase {

    @Test
    public void testMerge() throws Exception {
        String directory = "/tmp/HaloDBFileMergeTest/testMerge";

        int recordSize = 1024;
        int recordNumber = 20;

        HaloDBOptions options = new HaloDBOptions();
        options.maxFileSize = 10 * recordSize;
        options.mergeThresholdPerFile = 0.5;
        options.mergeThresholdFileNumber = 2;
        options.isMergeDisabled = true;

        HaloDB db = getTestDB(directory, options);

        byte[] data = new byte[recordSize - Record.Header.HEADER_SIZE - 8 - 8];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte)i;
        }

        Record[] records = new Record[recordNumber];
        for (int i = 0; i < recordNumber; i++) {
            byte[] key = Longs.toByteArray(i);
            byte[] value = TestUtils.concatenateArrays(data, key);
            records[i] = new Record(key, value);
            db.put(records[i].getKey(), records[i].getValue());
        }

        Set<Integer> fileIdsToMerge = db.listDataFileIds();

        List<Record> staleRecords = new ArrayList<>();
        List<Record> freshRecords = new ArrayList<>();

        for (int i = 0, j = 0; i < 5; i++, j++) {
            staleRecords.add(records[i]);
            staleRecords.add(records[i+10]);
        }

        for (int i = 5, j = 0; i < 10; i++, j++) {
            freshRecords.add(records[i]);
            freshRecords.add(records[i + 10]);
        }

        for (Record r : staleRecords) {
            db.put(r.getKey(), r.getValue());
        }

        HaloDBFile mergedFile = HaloDBFile.create(new File(directory), 1000, options, HaloDBFile.FileType.COMPACTED_FILE);
        CompactionJob compactionJob = new CompactionJob(fileIdsToMerge, mergedFile, db.getDbInternal());
        compactionJob.run();

        HaloDBFile.HaloDBFileIterator iterator = mergedFile.newIterator();

        List<Record> mergedRecords = new ArrayList<>();

        while (iterator.hasNext()) {
            mergedRecords.add(iterator.next());
        }

        Assert.assertTrue(mergedRecords.containsAll(freshRecords) && freshRecords.containsAll(mergedRecords));
    }
}
