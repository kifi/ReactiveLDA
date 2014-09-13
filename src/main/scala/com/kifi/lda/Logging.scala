package com.kifi.lda

import java.util.logging.Logger
import java.util.logging.ConsoleHandler

trait Logging {
  val log = Logger.getAnonymousLogger()
}
