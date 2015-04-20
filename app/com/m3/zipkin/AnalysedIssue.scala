package com.m3.zipkin

sealed trait AnalysedIssue

/**
 * Used to indicate that a Span has been flagged as an issue
 * because it is over the unignorable duration threshold
 */
case object UnignorableDuration extends AnalysedIssue

/**
 * Used to indicate that a Span has been flagged as an issue
 * because it the root span that is taking up the most time
 * within a given period of time
 */
case object CriticalSpan extends AnalysedIssue