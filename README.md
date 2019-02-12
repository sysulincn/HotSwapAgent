# HotSwapAgent

## 如何发布

在命令行下使用`gradlew distZip`生成要发布的文件：

build/distributions/hotswapAgent.zip

将zip包发布到linux机器上解压，给*start.sh*添加运行权限即可运行

## 如何使用
HotSwapAgent有三种使用方式，每种方式都依赖于特定的配置，以热加载server为例：
1. 热加载server的程序路径下30s内改动过的class文件; ***NOTE: 依赖于`config.xml`里面的`search_path`配置***
    ```bash
        ./start.sh server -i 30 
        ./start.sh server --interval 30
    ```
2. 热加载server的程序路径下"2018-12-11 15:58:00"这个时间之后改动过的class文件; ***NOTE: 依赖于`config.xml`里面的`search_path`配置***
    ```bash
        ./start.sh server -dt "2018-12-11 15:58:00"     
        ./start.sh server --daytime "2018-12-11 15:58:00"
    ```
3. 热加载某文件（例如ClassToRedefine.txt）下列出的class文件及文件夹下所有的class文件（包含所有子文件夹下的class）; ***NOTE: 依赖于指定的文件内容***
    ```bash
        ./start.sh server -f ClassToRedefine.txt  
        ./start.sh server --file ClassToRedefine.txt
    ``` 
    
## 如何配置
### 配置config.xml
`process` : 对于每一类想要热更的进程，都需要添加一个`process`节点

`pid_command` : 搜索此类进程的命令，这个命令返回应该就是进程ID，一行一个

`search_path` : 列出此类进程所加载的class所在的目录

具体例子见`src/dist/config.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<config>
    <process name="server"
          pid_command="ps -ef|grep javaE|grep -v grep|awk '{print $2}'"
          search_path="/home/server/javaExtensions/"
    />
</config>
 ```
 以上代码配置了名叫"server"的process以供HotSwapAgent使用，并指定了搜索进程id的命令以及搜索class路径
 
### 配置class列表文件
直接在文件中列出想要热加载的class文件，或者文件夹，如ClassToRedefine.txt:

    #文件支持开头为#的注释
    
    #下面的配置会让HotSwapAgent热加载仅WangZheBook.class一个类，不包含形如"WangZheBook$xxx.class"的子类及其他类
    /home/server/javaExtensions/com/altratek/altraserver/extensions/holiday/act2017/act20170217/WangZheBook.class
    
    #你也可以指定热加载整个目录下的类，注意这个目录会自动包含所有子目录下的类，因为HotSwapAgent会递归查找所有子目录
    /home/server/javaExtensions/com/altratek/altraserver/extensions/holiday/act2017/act20170217/

## 其余事项
1. 放置class文件的路径不一定需要符合class对应的包名，HotSwapAgent会自动解析class字节码里面的包名并热加载
2. HotSwapAgent的入侵包不会加载其他任何jar包（包括字节码解析包asm.jar），不会造成jar包版本冲突问题
3. 当前的实现方式仅支持linux系统，系统依赖包括来自jdk的tools.jar（不同操作系统需要不同jdk的具体实现）、读取文件更改时间的方式实现

## 问题
有问题请直接提交issue，或者联系作者林澄南 sysu_lincn@qq.com