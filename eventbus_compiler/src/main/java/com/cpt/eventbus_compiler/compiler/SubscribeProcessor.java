package com.cpt.eventbus_compiler.compiler;

import com.cpt.eventbus_annotation.annotation.Subscribe;
import com.cpt.eventbus_annotation.mode.EventBeans;
import com.cpt.eventbus_annotation.mode.SubscriberInfo;
import com.cpt.eventbus_annotation.mode.SubscriberMethod;
import com.cpt.eventbus_annotation.mode.ThreadMode;
import com.cpt.eventbus_compiler.utils.Constants;
import com.cpt.eventbus_compiler.utils.EmptyUtils;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

// AutoService则是固定的写法，加个注解即可”
//通过auto-service 中的AutoService可以自动生AutoService注解处理器，用来注册
//用来生成META- INF/services/javax. annotation. processing. Processor. 文件
@AutoService(Processor.class)
//允许/支持的注解类型，让注解处理器处理
@SupportedAnnotationTypes(Constants.SUBSCRIBE_ANNOTATION_TYPES)
//指定JDK编译版本
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions({Constants.PACKAGE_NAME, Constants.CLASS_NAME})
public class SubscribeProcessor extends AbstractProcessor {
    //操/ELement T具类(类两数、属性都是ELement)
    private Elements elementUtils;
    // type(类信息) T具类，包含用F操TypeMirror的工具方法
    private Types typeUtils;
    // Messager用来报告错误。警告和其他提示信息
    private Messager messager;
    //. 文件生成器类/资源。Filter用来创建新的类文件，class文件以及辅助文件
    private Filer filer;
    // APT包名
    private String packageName;

    // APT类名
    private String className;
    //临5/map存储。用来存放订阅方法信息。生成路由组类文件时间历
// key:组名"MainActivity", value:MainActivity中订阅方法 集合
    private final Map<TypeElement, List<ExecutableElement>> methodsByClass = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        //初始化
        elementUtils = processingEnvironment.getElementUtils();
        typeUtils = processingEnvironment.getTypeUtils();
        messager = processingEnvironment.getMessager();
        filer = processingEnvironment.getFiler();
//通过ProcessingEnvironment 去获取对应的参数
        Map<String, String> options = processingEnvironment.getOptions();
        if (!EmptyUtils.isEmpty(options)) {
            packageName = options.get(Constants.PACKAGE_NAME);
            className = options.get(Constants.CLASS_NAME);
            messager.printMessage(Diagnostic.Kind.NOTE, "packageName >》>" + packageName + "/ className 》》>" + className);
//必传参数判空(乱码问题:添加java控制台输出中文乱码)
            if (EmptyUtils.isEmpty(packageName) || EmptyUtils.isEmpty(className)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "注解处理器需要的参数为空，请在对应build. gradle配置参数");
            }
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
                     //获取所有被@Subscribe注解的元素集合
                 //一 -旦有求之上使用@Subscribe注解
        if (!EmptyUtils.isEmpty(set)) {
                //获取所有被@Subscribe注解的元素集合
            Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(Subscribe.class);
            if (!EmptyUtils.isEmpty(elements)) {
           //解析元素
                try {
                    parseElements(elements);
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return true;
        }

        return false;
    }

    private void parseElements(Set<? extends Element> elements) throws IOException {
//遍历节点
        for (Element element : elements) {
// @Subscribe注解只能在方法之上: (尽最避免使用/instanceof进行判断)
            if (element.getKind() != ElementKind.METHOD) {
                messager.printMessage(Diagnostic.Kind.ERROR, "仅解析@Subscribe主解在方法上元素");
                return;
            }
            ExecutableElement method = (ExecutableElement) element;
//检查方法，条件:订阅方法必须是非静态的， 公开的。 参数只能有一 个
            if (checkHasNoErrors(method)) {
//获取封装订阅方法的类(方法上一个节点)
                TypeElement classElement = (TypeElement) method.getEnclosingElement();
//以类名为key，保存订阅方法
                List<ExecutableElement> methods = methodsByClass.get(classElement);
                if (methods == null) {
                    methods = new ArrayList<>();
                    methodsByClass.put(classElement, methods);
                }
                methods.add(method);
            }
            messager.printMessage(Diagnostic.Kind.NOTE, "遍历注解方法:" + method.getSimpleName().toString());
        }
//通过ELement工具类，获取SubscriberInfoIndex 类型
        TypeElement subscriberIndexType = elementUtils.getTypeElement(Constants.SUBSCRIBERINFO_INDEX);
//生成类文件
        createFile(subscriberIndexType);

    }


    private boolean checkHasNoErrors(ExecutableElement element) {


        //不能为static静态方法
        if (element.getModifiers().contains(Modifier.STATIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "订阅事件方法不能是static静态方法", element);
            return false;
        }
//必须是public修饰的方法
        if (!element.getModifiers().contains(Modifier.PUBLIC)) {
            messager.printMessage(Diagnostic.Kind.ERROR, "订阅事件方法必须是pub1ic修饰的方法", element);
            return false;
        }
//订阅事件方法必须只有一一个参数
        List<? extends VariableElement> parameters = ((ExecutableElement) element).getParameters();
        if (parameters.size() != 1) {
            messager.printMessage(Diagnostic.Kind.ERROR, "订阅事件方法有且仅有一一个参数", element);
            return false;
        }
        return true;
    }

    private void createFile(TypeElement subscriberIndexType) throws IOException {

        CodeBlock.Builder codeBlock = CodeBlock.builder();
        codeBlock.addStatement("$N = new $T<$T , $T >()",
                Constants.FIELD_NAME,
                HashMap.class,
                Class.class,
                SubscriberInfo.class);
        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : methodsByClass.entrySet()) {
            CodeBlock.Builder contentBlock = CodeBlock.builder();
            CodeBlock contentCode = null;
            String format;
            for (int i = 0; i < entry.getValue().size(); i++) {
                ExecutableElement element = entry.getValue().get(i);
                //获取每个方法上的@Subscribe注解值
                Subscribe subscribe = element.getAnnotation(Subscribe.class);
                //获取订阅方法所有参数
                List<? extends VariableElement> parameters = element.getParameters();
                //获取订阅事件的方法名
                String methodName = element.getSimpleName().toString();
                TypeElement parameterElement = (TypeElement) typeUtils.asElement(parameters.get(0).asType());
                //如果最后一个添加 无需逗号结尾
                if (i == entry.getValue().size() - 1) {
                    format = "new $T($T.class , $S ,$T.class,$T.$L ,$L ,$L)";
                } else {
                    format = "new $T($T.class , $S ,$T.class,$T.$L ,$L ,$L) ,\n";
                }

                contentCode = contentBlock.add(format,
                        SubscriberMethod.class,
                        ClassName.get(entry.getKey()),
                        methodName,
                        ClassName.get(parameterElement),
                        ThreadMode.class,
                        subscribe.threadMode(),
                        subscribe.priority(),
                        subscribe.sticky()
                ).build();
            }
            if (contentCode != null) {
                codeBlock.beginControlFlow("putIndex(new $T($T.class, new $T[]",
                        EventBeans.class,
                        ClassName.get(entry.getKey()),
                        SubscriberMethod.class)
                        .add(contentCode)
                        .endControlFlow("))");
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, "注解处理器双层循环发生错误");
            }
        }
        //全局属性 map<Class<?>.SubscriberMethod>
        TypeName fieldType = ParameterizedTypeName.get(
                ClassName.get(Map.class), //map
                ClassName.get(Class.class), //map<class,
                ClassName.get(SubscriberInfo.class)//map<class,SubscriberMethod>
        );

        //putIndex方法参数 putIndex(SubscriberInfo info)
        ParameterSpec putIndexParameter = ParameterSpec.builder(
                ClassName.get(SubscriberInfo.class),
                Constants.PUTINDEX_PARAMETER_NAME)
                .build();
        //putIndex方法配置 private static putIndex( SubscriberInfo info)
        MethodSpec.Builder putIndexBuidler = MethodSpec
                .methodBuilder(Constants.PUTINDEX_METHOD_NAME) //方法名
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)  //private static 修饰符
                .addParameter(putIndexParameter); //添加方法参数
        //putIndex方法内容
        putIndexBuidler.addStatement("$N.put($N.getSubscriberClass(),$N)",
                Constants.FIELD_NAME,
                Constants.PUTINDEX_PARAMETER_NAME,
                Constants.PUTINDEX_PARAMETER_NAME);

        ParameterSpec getSubscriberInfoParameter = ParameterSpec.builder(
                ClassName.get(Class.class),
                Constants.GETSUBSCRIBERINFO_PARAMETER_NAME
        ).build();

        MethodSpec.Builder getSubscriberInfoBuidler = MethodSpec
                .methodBuilder(Constants.GETSUBSCRIBERINFO_METHOD_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(getSubscriberInfoParameter)
                .returns(SubscriberInfo.class);

        getSubscriberInfoBuidler.addStatement("return $N.get($N)",
                Constants.FIELD_NAME,
                Constants.GETSUBSCRIBERINFO_PARAMETER_NAME
        );
//putIndexParameter
        //构建类
        TypeSpec typeSpec = TypeSpec.classBuilder(className)
                //实现SubscriberInfoIndex按口
                .addSuperinterface(ClassName.get(subscriberIndexType))
                //该类的修饰符
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                //添加静态块(很少用的api )
                .addStaticBlock(codeBlock.build())
                //全局属性: private static final Map<Class<?>, SubscriberMethod> SUBSCRIBER_ INDEX
                .addField(fieldType, Constants.FIELD_NAME, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                //第一个方法:加入全局Map集合
                .addMethod(putIndexBuidler.build())
                //  lyx1997me第二个方法:通过订阅者对象(MainActivity.class) 获取所有订阅方法
                .addMethod(getSubscriberInfoBuidler.build())
                .build();
//生成类文件: EventBusIndex
        JavaFile.builder(packageName, //包名
                typeSpec) //类构建完成
                .build() // JavaFile 构建完成
                .writeTo(filer); //. 文件生成器开始生成类文件
    }
}
