package org.apache.maven.plugin.surefire.report;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.maven.plugin.surefire.runorder.StatisticsReporter;
import org.apache.maven.surefire.report.*;
import org.apache.maven.surefire.util.internal.ByteBuffer;

/**
 * Reports data for a single test set.
 * <p/>
 */
public class TestSetRunListener
    implements RunListener, ConsoleOutputReceiver, ConsoleLogger
{
    private final TestSetStatistics testSetStatistics;

    private final RunStatistics globalStatistics;

    private final TestSetStats detailsForThis;

    private final MulticastingReporter multicastingReporter;

    private final List<ByteBuffer> testStdOut = Collections.synchronizedList( new ArrayList<ByteBuffer>() );

    private final List<ByteBuffer> testStdErr = Collections.synchronizedList( new ArrayList<ByteBuffer>() );

    private final TestcycleConsoleOutputReceiver consoleOutputReceiver;

    public TestSetRunListener(AbstractConsoleReporter consoleReporter, AbstractFileReporter fileReporter,
                              XMLReporter xmlReporter, TestcycleConsoleOutputReceiver consoleOutputReceiver, StatisticsReporter statisticsReporter,
                              RunStatistics globalStats, boolean trimStackTrace)
    {
        List<Reporter> reporters = new ArrayList<Reporter>();
        this.detailsForThis = new TestSetStats(trimStackTrace);
        if ( consoleReporter != null )
        {
            reporters.add( consoleReporter );
        }
        if ( fileReporter != null )
        {
            reporters.add( fileReporter );
        }
        if ( xmlReporter != null )
        {
            reporters.add( xmlReporter );
        }
        if ( statisticsReporter != null )
        {
            reporters.add( statisticsReporter );
        }

        this.consoleOutputReceiver = consoleOutputReceiver;

        multicastingReporter = new MulticastingReporter( reporters );
        this.testSetStatistics = new TestSetStatistics();
        this.globalStatistics = globalStats;
    }

    public void info( String message )
    {
        multicastingReporter.writeMessage( message );
    }

    public void writeTestOutput( byte[] buf, int off, int len, boolean stdout )
    {
        ByteBuffer byteBuffer = new ByteBuffer( buf, off, len );
        if ( stdout )
        {
            testStdOut.add( byteBuffer );
        }
        else
        {
            testStdErr.add( byteBuffer );
        }
        consoleOutputReceiver.writeTestOutput( buf, off, len, stdout );
    }

    public void testSetStarting( ReportEntry report )
    {
        detailsForThis.testSetStart();
        multicastingReporter.testSetStarting(report);
        consoleOutputReceiver.testSetStarting( report);
    }

    public void clearCapture()
    {
        testStdErr.clear();
        testStdOut.clear();
    }

    public void testSetCompleted( ReportEntry report )
    {
        multicastingReporter.testSetCompleted( report, detailsForThis);
        consoleOutputReceiver.testSetCompleted( report);
        detailsForThis.reset();
        multicastingReporter.reset();
        globalStatistics.add(testSetStatistics);
        testSetStatistics.reset();
    }

    // ----------------------------------------------------------------------
    // Test
    // ----------------------------------------------------------------------

    public void testStarting( ReportEntry report )
    {
        detailsForThis.testStart();
        multicastingReporter.testStarting( report );
    }

    public void testSucceeded( ReportEntry reportEntry )
    {
        detailsForThis.testEnd();
        testSetStatistics.incrementCompletedCount();
        multicastingReporter.testSucceeded( reportEntry, detailsForThis);
        clearCapture();
    }

    public void testError( ReportEntry reportEntry )
    {
        detailsForThis.incrementErrorsCount();
        detailsForThis.testEnd();

        multicastingReporter.testError( reportEntry, getAsString( testStdOut ), getAsString( testStdErr ), detailsForThis);
        testSetStatistics.incrementErrorsCount();
        testSetStatistics.incrementCompletedCount();
        globalStatistics.addErrorSource( reportEntry.getName(), reportEntry.getStackTraceWriter() );
        clearCapture();
    }

    public void testFailed( ReportEntry reportEntry )
    {
        detailsForThis.incrementFailureCount();
        detailsForThis.testEnd();

        multicastingReporter.testFailed(reportEntry, getAsString(testStdOut), getAsString(testStdErr),  detailsForThis);
        testSetStatistics.incrementFailureCount();
        testSetStatistics.incrementCompletedCount();
        globalStatistics.addFailureSource(reportEntry.getName(), reportEntry.getStackTraceWriter());
        clearCapture();
    }

    // ----------------------------------------------------------------------
    // Counters
    // ----------------------------------------------------------------------

    public void testSkipped( ReportEntry reportEntry )
    {
        detailsForThis.incrementSkippedCount();
        detailsForThis.testEnd();

        clearCapture();
        testSetStatistics.incrementSkippedCount();
        testSetStatistics.incrementCompletedCount();
        multicastingReporter.testSkipped(reportEntry,  detailsForThis);
    }

    public void testAssumptionFailure( ReportEntry report )
    {
        testSkipped(report);
    }

    public String getAsString( List<ByteBuffer> byteBufferList )
    {
        StringBuilder stringBuffer = new StringBuilder();
        // To avoid getting a java.util.ConcurrentModificationException while iterating (see SUREFIRE-879) we need to
        // iterate over a copy or the elements array. Since the passed in byteBufferList is always wrapped with
        // Collections.synchronizedList( ) we are guaranteed toArray() is going to be atomic, so we are safe.
        for ( Object byteBuffer : byteBufferList.toArray() )
        {
            stringBuffer.append( byteBuffer.toString() );
        }
        return stringBuffer.toString();
    }
}
