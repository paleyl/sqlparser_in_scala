# sqlparser_in_scala
A simple sql parser in scala, output sql structure in json format.

Support select/from/where/group by/order by/join & union. 

Input the following command in your commandline (here we use windows command for example)
```
1. git clone https://github.com/paleyl/sqlparser_in_scala.git
2. cd sqlparser_in_scala
3. mvn package
4. java -cp target\smalldata-core-0.1-shaded.jar net.smalldata.sql.sqlparser.SqlParser -inputFile data\sql\sql.txt
```

And you will see a json format output for the input sql,

such as 

```
{
    "name":"--root",
    "from":{
        "join":{
            "subqueries":[
                {
                    "name":"t1",
                    "from":{
                        "dataSource":"table1"
                    },
                    "where":"uin > 0",
                    "select":{
                        "fields":[
                            "uin",
                            "pay_cnt"
                        ],
                        "groupby":[
                            "--empty"
                        ]
                    },
                    "orderBy":[
                        "--empty"
                    ],
                    "insert":"--empty",
                    "bracket":{

                    }
                },
                {
                    "name":"t2",
                    "from":{
                        "dataSource":"table2"
                    },
                    "where":"uin < 100",
                    "select":{
                        "fields":[
                            "uin",
                            "sum(statement0) AS statement0"
                        ],
                        "groupby":[
                            "--empty"
                        ]
                    },
                    "orderBy":[
                        "--empty"
                    ],
                    "insert":"--empty",
                    "bracket":{
                        "statement0":"pay_amt"
                    }
                },
                {
                    "name":"t3",
                    "from":{
                        "dataSource":"table3"
                    },
                    "where":"rcv_amt < 100",
                    "select":{
                        "fields":[
                            "uin",
                            "rcv_amt"
                        ],
                        "groupby":[
                            "--empty"
                        ]
                    },
                    "orderBy":[
                        "--empty"
                    ],
                    "insert":"--empty",
                    "bracket":{

                    }
                }
            ],
            "join types":[
                " join ",
                " join "
            ],
            "on conditions":[
                "t1.uin = t2.uin",
                "t1.uin = t3.uin"
            ]
        }
    },
    "where":"pay_amt > 10",
    "select":{
        "fields":[
            "t1.uin",
            "pay_amt",
            "rcv_amt",
            "pay_cnt"
        ],
        "groupby":[
            "--empty"
        ]
    },
    "orderBy":[
        "--empty"
    ],
    "insert":"--empty",
    "bracket":{
        "statement0":"select uin, pay_cnt from table1 where uin > 0",
        "statement4":"t1.uin = t3.uin",
        "statement1":"select uin, sum(pay_amt) AS pay_amt from table2 where uin < 100 group by uin",
        "statement2":"t1.uin = t2.uin",
        "statement3":"select uin, rcv_amt from table3 where rcv_amt < 100"
    }
}
```
