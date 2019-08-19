package net.smalldata.sql.sqlparser

import java.util.regex._

import scala.io.Source
import scala.util.matching.Regex

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.log4j.{LogManager}

/*
 * sql parse: 解析sql，包含subquery。
 * 1. 进行括号替换，
 * 2. 解析from，在解析from的过程中会判断有没有join/union，如果有的话，递归解析subquery
 * 3. 解析where
 * 4. 解析select
 * 5. 解析order by
 * 6. 返回sqlQuery
 */

object FromType extends Enumeration {
    type FromType = Value
    val Join = Value(1)
    val Union = Value(2)
    val SingleQuery = Value(3)
    val Unknown = Value(4)
}

import FromType._

class Strings(str: String) {
  def addQuotation = "\"" + str + "\"" 
  def addParentheses = "{" + str + "}"
}

object impStrings {
  implicit def strings(str: String): Strings = new Strings(str) 
}

import impStrings.strings

class UnionClause(subQueryArray: Array[SqlQuery], 
    unionTypeArray: Array[String]) {
  override def toString(): String = {    
    s"""{"subqueries": [ ${subQueryArray.mkString(", ")} ], """ + 
    s""""union types": [ ${unionTypeArray.map(l => l.addQuotation).mkString(", ")} ]} """
  }  
}

class SingleQueryClause() {
  
} 
  
class FromClause(val fromType: FromType,
    val join: JoinClause,
    val union: UnionClause,
    val singleQuery: SqlQuery,
    val dataSource: String) {
  override def toString(): String = {
    val fromString = 
      if(fromType == Join) "\"join\": " + join.toString
      else if(fromType == Union) "\"union\": " + union.toString
      else if(fromType == SingleQuery) "\"singleQuery\": " + singleQuery.toString
      else "\"dataSource\": " + dataSource.addQuotation
    s"${fromString.addParentheses}"
  }
}

class InsertClause(path: String) {
  
}

class JoinClause(val subQueryArray: Array[SqlQuery],
    val joinTypeArray: Array[String],
    val onConditionArray: Array[String]) {
  override def toString(): String = {      
    s"""{"subqueries": [ ${subQueryArray.mkString(", ")} ], """ + 
    s""""join types": [ ${joinTypeArray.map(l => l.addQuotation).mkString(", ")} ], """ + 
    s""""on conditions": [ ${onConditionArray.map(l => l.addQuotation).mkString(", ")} ]}"""
  }  
}

class SelectClause(val selectFields: Array[String],
    val groupByFields: Array[String]) {  
  override def toString(): String = {
    s"""{"fields": [${selectFields.map(l => l.addQuotation).mkString(", ")}], """ +
    s""""groupby": [${groupByFields.map(l => l.addQuotation).mkString(", ")}]}"""
  }  
}

class SqlQuery(
    val queryName: String,
    val from: FromClause,
    val where: String,
    val select: SelectClause,
    val orderBy: Array[String],
    val insert: String,
    val bracketMap: Map[String, String]
) {
  override def toString(): String = {
    s"""{"name": "$queryName", "from": $from, "where": "$where", "select": $select, """ + 
    s""""orderBy": [${orderBy.map(l => l.addQuotation).mkString(", ")}], "insert": "$insert", """ + 
    s""""bracket": {${bracketMap.toArray.map(l => l._1.addQuotation + ": " + l._2.addQuotation).mkString(", ")}}}"""
  }
}


object SqlParser {
  val logger = LogManager.getLogger(this.getClass())

  @Parameter(names = Array("-inputFile"), description = "the inputFile of sql string)", arity = 1)
  private val inputFile = ""; 
  
  def parseRegex(query: String, regex: String): Matcher = {
    val reg = Pattern.compile(regex)
    val matcher = reg.matcher(query)
    matcher
  }
  
  def calcBracketDepth(input: String): Array[Int] = {
    var cnt = 0
    val bracketDepth = for(char <- input) yield {
      if(char.toString == "("){ 
        cnt += 1; cnt
      }
      else if (char.toString == ")"){
        cnt -= 1; cnt + 1
      }
      else 
        0
    }
    bracketDepth.toArray
  }
  
  def subStituteBracket(query: String): (Map[String, String], String) = {
    val bracketDepth = calcBracketDepth(query)
    val level1Index = bracketDepth.zipWithIndex.filter(_._1 == 1).map(_._2)
    val level1Length = level1Index.length
    assert(level1Length % 2 == 0, s"$query has imbalanced bracket")
    val bracketMap = { for(i <- 0 to level1Length / 2 - 1)  yield 
      (s"statement$i", query.slice(level1Index(2 * i) + 1, level1Index(2 * i + 1)))
    }.toMap
    var replacedQuery = query
    bracketMap.toArray.foreach(l => {replacedQuery = replacedQuery.replace(l._2, l._1)})
    logger.debug(bracketMap.toArray.map(l => l._1 + ":" + l._2).mkString("\n"))
    logger.debug(replacedQuery) 
    (bracketMap.toMap, replacedQuery)
  }

  def restoreBracket(str: String, bracketMap: Map[String, String]): String = {
    var resultStr = str
    for(key <- bracketMap.keys.toArray) {
      logger.debug(s"$key, ${bracketMap(key)}\n${resultStr}")      
      resultStr = resultStr.replace(key, bracketMap(key))
    }
    resultStr
  }
  
  def parseJoin(bracketMap: Map[String, String], query: String): JoinClause = {
    val joinNum = query.split(" join ").length - 1
    val regex = "(.+?)" + "( join | left join | right join | full join )(.+?)( on )(.+?)" * joinNum + "(--ENDOFSQL)"
    val matcher = parseRegex(query + "--ENDOFSQL", regex)
    
    val (queryStringArray, joinType, onCondition) = 
    if(matcher.find()) {
      val tmp = {for(i <- 0 to joinNum - 1) yield matcher.group(i * 4 + 3)}.toArray
      (
        Array(matcher.group(1)) ++ tmp,
        {for(i <- 0 to joinNum - 1) yield matcher.group(i * 4 + 2)}.toArray,
        {for(i <- 0 to joinNum - 1) yield matcher.group(i * 4 + 5)}.toArray
      )
    } else {(null, null, null)}
    val subQueryArray = for (queryString <- queryStringArray) yield {
      val regex = "(\\()([^\\(\\)].+)(\\))(.+)"
      val matcher = parseRegex(queryString, regex)
      println(matcher.find())
      val queryKey = matcher.group(2)
      val alias = matcher.group(4).trim
      val queryBody = " " + bracketMap.getOrElse(queryKey, "") + "--ENDOFSQL"
      logger.debug(s"begin to parse $queryBody")
      parse(queryBody, alias)
    }
    val onConditionArray = for (onString <- onCondition) yield {
      val regex = "(\\()([^\\(\\)].+)(\\))"
      val matcher = parseRegex(onString, regex)
      if(matcher.find()) bracketMap.getOrElse(matcher.group(2), "") else onString
    }    
    logger.debug("sub queries are: \n" + subQueryArray.mkString("\n"))
    logger.debug("join types are: \n" + joinType.mkString("\n"))
    logger.debug("on conditions are: \n" + onConditionArray.mkString("\n"))       
    new JoinClause(subQueryArray, joinType, onConditionArray)
  }
  
  def parseUnion(query: String): UnionClause = {
    val unionNum = query.split(" union ").length - 1
//    val regex = "(.+?)" + "( union (all )?)(.+?)" * unionNum + "(--ENDOFSQL)"    
    val regex = "(.+?)" + "( union-all | union )(.+?)" * unionNum + "(--ENDOFSQL)"
    val matcher = parseRegex(query.replace("union all", "union-all") + "--ENDOFSQL", regex)
    val (queryStringArray, unionTypeArray) = 
      if(matcher.find()) {
        val tmp = {for(i <- 0 to unionNum - 1) yield matcher.group(i * 2 + 1)}.toArray
        (
          Array(matcher.group(1)) ++ tmp,
          {for(i <- 0 to unionNum - 1) yield matcher.group(i * 2 + 2)}.toArray
        )
      } else {(null, null)}
    val subQueryArray = queryStringArray.map(l => l + "--ENDOFSQL").zipWithIndex.map(l => parse(l._1, "union" + l._2))
    new UnionClause(subQueryArray, unionTypeArray)
  }
  
  def parseFrom(bracketMap: Map[String, String], query: String): FromClause = {    
    val regex = "(from)(.+?)( where |--ENDOFSQL| order by | group by )"    
    val matcher = parseRegex(query, regex)
    val fromStatement = if(matcher.find() && matcher.groupCount() == 3) {
      matcher.group(2).trim
    } else "--empty"
    logger.debug(s"from statement is $fromStatement")
    val restoreStatement = restoreBracket(
        fromStatement.split(" ")(0).replace("(", "").replace(")", ""), bracketMap)
    logger.debug(s"restoreStatement is $restoreStatement")
    val fromClause = 
    if (fromStatement.contains(" join ")) {
      val join = parseJoin(bracketMap, fromStatement)
      new FromClause(Join, join, null, null, null)
    } else if (restoreStatement.contains(" union ")) {
      val union = parseUnion(restoreStatement)
      new FromClause(Union, null, union, null, null)
    } else if (restoreStatement.contains(" from ")){
      val singleQuery = parse(restoreStatement, "singleQuery")
      new FromClause(SingleQuery, null, null, singleQuery, null)
    } else {
      new FromClause(Unknown, null, null, null, fromStatement)
    }
    fromClause
  }
  
  def parseSelect(bracketMap: Map[String, String], query: String): SelectClause = {
    val regex = "([^\\w]select)(.+?)( from )"
    val matcher = parseRegex(query, regex)
    val selectStatement = if(matcher.find() && matcher.groupCount() == 3) {
      matcher.group(2).trim
    } else "--empty"
    logger.debug(s"select statement is $selectStatement")  
    val groupByRegex = "( group by )(.+?)( order by |--ENDOFSQL)"
    val groupByStatement = if(matcher.find() && matcher.groupCount() == 3) {
      matcher.group(2).trim
    } else "--empty"  
    logger.debug(s"group by statement is $groupByStatement")
    new SelectClause(selectStatement.split(",").map(_.trim), 
        groupByStatement.split(",").map(_.trim))
  }
  
  def parseWhere(bracketMap: Map[String, String], query: String): String = {
    val regex = "([^\\w]where)(.+?)( group by |having| order by |--ENDOFSQL)"
    val matcher = parseRegex(query, regex)
    val whereStatement = if(matcher.find() && matcher.groupCount() == 3) {
      matcher.group(2).trim
    } else "--empty"
    logger.debug(s"where statement is $whereStatement")      
    whereStatement       
  }
  
  def parseOrderBy(bracketMap: Map[String, String], query: String): Array[String] = {
    val regex = "( order by )(.+?)(--ENDOFSQL)"
    val matcher = parseRegex(query, regex)
    val statement = if(matcher.find() && matcher.groupCount() == 3) {
      matcher.group(2).trim
    } else "--empty"
    logger.debug(s"order by statement is $statement")  
    statement.split(" ").map(_.trim)
  }  
  
  def parse(query: String, queryName: String = "--root"): SqlQuery = {
    val insertRegex = "(insert into)(.+?)( select )"
    val matcher = parseRegex(query, insertRegex)
    val outputPath = if(matcher.find() && matcher.groupCount() == 3) {
      matcher.group(2).trim
    } else "--empty"
    logger.info(s"output path is $outputPath")
    val (bracketMap, replacedQuery) = subStituteBracket(query)
    logger.info(s"replaced query is: $replacedQuery")
    val from = parseFrom(bracketMap, replacedQuery)
    val where = parseWhere(bracketMap, replacedQuery)
    val select = parseSelect(bracketMap, replacedQuery)
    val orderBy = parseOrderBy(bracketMap, replacedQuery)
    val sqlQuery = new SqlQuery(queryName, from, where, select, orderBy, outputPath, bracketMap)
    sqlQuery
  }
  
  def test(query: String) {
    logger.info(parse(query).toString())
  }
  
  def main(args: Array[String]) {
    val inputSqlFile = if(inputFile != "") inputFile else
        System.getProperty("user.dir") + // 默认使用本地文件
        "/data/sql/sql.txt"
    val tmpSql = Source.fromFile(inputSqlFile).getLines()
    .toArray.map(l => l.split("--")(0)).mkString(" ") + "--ENDOFSQL"
    .replace("\t", " ")
    val sql = " " + "\\s+".r.replaceAllIn(tmpSql, " ")
    logger.debug(sql)
    test(sql)
  }
  
}