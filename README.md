# Karmock
Karmok consists of an intelliJ plugin and a supporting library dependency which can help you manage interfaces in your unit tests for a KMP project. It is currently experimental and is not our vision for a complete KMP mocking solution, but it is definitely helpful for now.

Karmok allows you to generate a mock implementation of an interface which is capable of verifying method calls and overriding their results.
## Installation
### karmok-library
The library includes all the plumbing code which performs the recording and configuration of your mocks. Code generated by the plugin will expect this library to be present. 
```kotlin
implementation(“co.touchlab:karmok-library:0.1.7”)
```
### idea-plugin
This is the plugin which will generate mock implementations for you . To test, go to 'Tasks > intellij > runIde'. You'll need to create a project, or import an existing one. You'll need to add the mavenLocal repo and add the library dependency to use the generated mocks.

To use the plugin, create a class that implements an interface. Option+Enter on the class name and you should see "Implement Mock Members". Select the members you want to include (which should be all, but still).

![pop up](intellijpopup.png "Intellij Pop Up")

You can build the plugin with 'buildPlugin', which makes a zip in 'build/distributions'. You can import that from Intellij's settings pane.

![import](pluginimport.png "Intellij Import")
## Usage
Say we want to create a mock implementation for the following interface:

```kotlin
interface MyInterface {
    val name: String
    fun hey(): Long
}
```

The first step will be to create an empty class with the interface in it’s signature

```kotlin
class MockMyInterface: MyInterface {

}
```
The IDE will complain that `MockMyInterface` doesn’t implement the necessary methods to fulfill `MyInterface`. Rather than write them ourselves, we will use the Karmok plugin to fill in the class. Press Option+Enter on the class name to open the quick fix dialog. In the list you should see “Implement Mock Members”. Select all the members and press ok to generate the body
The intellij plugin will add implementation inside `TestingImpl`

```kotlin
class MockMyInterface: MyInterface {
    internal val mock = InnerMock()
    override val name: String by mock.name
    override fun hey(): Long {
        return mock.hey.invoke({ hey() }, listOf())
    }

    class InnerMock(delegate: Any? = null) : MockManager(delegate) {
        internal val name = MockPropertyRecorder<TT, String>({ name }) {}
        internal val hey = MockFunctionRecorder<TT, Long>()
    }
}
```

The implementation includes an inner class called `InnerMock`, which extends `MockManager`. Each function and property of the interface gets a `Recorder` instance in `InnerMock`. These classes are included in the karmok-library dependency which must be present. The recorders can be configured with what to be returned when called, and can be verified after you are done with interaction.

```kotlin
@Test
fun arst(){
    val tt = TestingImpl()
    tt.mock.arst.returnOnCall("arst")
    tt.mock.hey.returns(22L)
    
    assertEquals(tt.hey(), 22L)
}
```

There are 2 recorders, `MockFunctionRecorder` and `MockPropertyRecorder`. I've added a set of interfaces for each that are focused on config and verify operations. For example, for properties:

```kotlin
interface MockPropertyConfigure<RT> {
    fun throwOnCall(t: Throwable)
    fun returnOnCall(rt: RT)
}

interface MockPropertyVerify<RT> {
    val getCalled: Boolean
    fun setCalled(rt: RT): Boolean
    val calledCountGet: Int
    val calledCountSet: Int
}
```

These are separate because the next goal was to improve the config and verify steps, but that is incomplete. It would look something like the following:

```kotlin
@Test
fun arst(){
    val tt = TestingImpl()
    tt.mock.config {
        arst.returnOnCall("arst")
        hey.returns(22L)
    }
    
    assertEquals(tt.hey(), 22L)
    
    tt.mock.verify {
        assertEquals(hey.calledCount, 1)
    }
}
```

Not implemented, but that's why there are multiple interfaces. Currently, the config and verify functions can feel a little confusing because they both show up on autocomplete, but you'll probably get used to it.
## Issues
Currently the plugin does not add the imports for the inserted plumbing code. This is technically not too difficult (I have example code from Kapture), but it's also not super critical. To add later.
