/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.graphql;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import graphql.GraphQLException;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

public class Scalars {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);

    private static GraphQLScalarType graphQLDateTime = GraphQLScalarType.newScalar().name("DateTime")
            .description("An ISO-8601 encoded UTC date time string. Example value: \"2019-07-03T20:47:55Z\".")
            .coercing(new Coercing<Object, Object>() {

                @Override
                public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {
                    if (dataFetcherResult instanceof String) {
                        if (dataFetcherResult == "" || dataFetcherResult == null) {
                            return null;
                        }
                        return Timestamp.valueOf((String) dataFetcherResult).getTime();
                    } else if (dataFetcherResult instanceof Long) {
                        return new Timestamp((Long) dataFetcherResult).getTime();
                    } else if (dataFetcherResult instanceof Timestamp) {
                        return formatDateTimeToUTC((Timestamp) dataFetcherResult);
                    }
                    return null;
                }

                @Override
                public Object parseValue(Object input) throws CoercingParseValueException {
                    if (input instanceof String) {
                        return Timestamp.valueOf((String) input);
                    } else if (input instanceof Long) {
                        return new Timestamp((Long) input);
                    } else if (input instanceof Timestamp) {
                        return input;
                    }
                    return null;
                }

                @Override
                public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                    if (input instanceof StringValue) {
                        return Timestamp.valueOf(((StringValue) input).getValue());
                    } else if (input instanceof IntValue) {
                        BigInteger value = ((IntValue) input).getValue();
                        // Check if out of bounds.
                        if (value.compareTo(LONG_MIN) < 0 || value.compareTo(LONG_MAX) > 0) {
                            throw new GraphQLException(
                                    "Int literal is too big or too small for a long, would cause overflow");
                        }
                        return new Timestamp(value.longValue());
                    }
                    return null;
                }

                private String formatDateTimeToUTC(Timestamp ts) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    return sdf.format(ts);
                }

            }).build();

    /**
     * @return the graphQLDateTime
     */
    public static GraphQLScalarType getGraphQLDateTime() {
        return graphQLDateTime;
    }
}
