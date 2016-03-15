package org.anormcypher

case class MetaDataItem(column: String, nullable: Boolean, clazz: String)

case class MetaData(ms: List[MetaDataItem]) {

  def get(columnName: String) = dictionary.get(columnName.toUpperCase)

  private lazy val dictionary: Map[String, (String, Boolean, String)] = {
    ms.map { m =>
      (m.column.toUpperCase, (m.column, m.nullable, m.clazz))
    }.toMap
  }

  lazy val columnCount = ms.size

  lazy val availableColumns: List[String] = ms.map(_.column)

}
