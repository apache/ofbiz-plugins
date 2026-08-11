/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */
package org.apache.ofbiz.content.test

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.ofbiz.content.search.SearchWorker
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.testtools.JunitJupiterTest
import org.apache.ofbiz.testtools.JupiterTestHelper
import org.junit.jupiter.api.Test

@JunitJupiterTest
class LuceneTests implements JupiterTestHelper {

    @Test
    void testSearchTermHand() {
        Map ctx = [
                contentId: 'LuceneCONTENT',
                userLogin: userLogin
        ]
        Map resp = dispatcher.runSync('indexContentTree', ctx)
        assert ServiceUtil.isSuccess(resp) :
                ServiceUtil.isError(resp) ? ServiceUtil.getErrorMessage(resp) : 'Could not init search index'

        try {
            Thread.sleep(3000) // sleep 3 seconds to give enough time to the indexer to process the entries
        } catch (InterruptedException e) {
            logError("Thread interrupted :${e}")
        }

        Directory directory = FSDirectory.open(new File(SearchWorker.getIndexPath('content')).toPath())

        DirectoryReader reader = null
        try {
            reader = DirectoryReader.open(directory)
        } catch (Exception e) {
            throw new AssertionError("Could not open search index: ${directory}" as String)
        }

        BooleanQuery.Builder combQueryBuilder = new BooleanQuery.Builder()
        String queryLine = 'hand'

        IndexSearcher searcher = new IndexSearcher(reader)
        Analyzer analyzer = new StandardAnalyzer()

        QueryParser parser = new QueryParser('content', analyzer)
        Query query = parser.parse(queryLine)
        combQueryBuilder.add(query, BooleanClause.Occur.MUST)
        BooleanQuery combQuery = combQueryBuilder.build()

        TopDocs topDocs = searcher.search(combQuery, 10)

        assert topDocs.totalHits.value == 1 : 'Only 1 result expected from the testdata'
    }

}
