/*
 * Copyright (C) 2016-2018 Cafienne B.V. <https://www.cafienne.io/bounded>
 */

package io.cafienne.bounded.runtime

import java.io.{File, FileWriter}
import java.util.UUID

import io.cafienne.bounded.RuntimeInfo

import scala.io.Source

object RuntimeInfoLoader {

  private def newId = UUID.randomUUID().toString.replaceAll("-", "")

  def apply(file: File): RuntimeInfo = {
    val id = if (file.exists()) {
      Source.fromFile(file).getLines().mkString
    } else {
      val runtimeId = newId
      val fw        = new FileWriter(file)
      fw.write(runtimeId)
      fw.close()
      runtimeId
    }
    RuntimeInfo(id)
  }

}
