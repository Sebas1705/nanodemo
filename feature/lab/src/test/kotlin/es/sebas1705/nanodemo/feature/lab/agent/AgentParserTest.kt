package es.sebas1705.nanodemo.feature.lab.agent

import es.sebas1705.nanodemo.feature.lab.agent.tools.ToolArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentParserTest {

    private fun act(raw: String) = AgentParser.parse(raw) as AgentReply.Act
    private fun final(raw: String) = AgentParser.parse(raw) as AgentReply.Final

    @Test
    fun `a standard action`() {
        val reply = act("Thought: I need to calculate\nAction: calculator\nAction Input: {\"expression\": \"2+2\"}")
        assertEquals("I need to calculate", reply.thought)
        assertEquals("calculator", reply.tool)
        assertEquals("{\"expression\": \"2+2\"}", reply.input)
    }

    @Test
    fun `a standard final answer`() {
        val reply = final("Thought: done\nFinal Answer: 4")
        assertEquals("done", reply.thought)
        assertEquals("4", reply.answer)
        assertEquals(false, reply.implicit)
    }

    @Test
    fun `a reply with no labels is an implicit final answer`() {
        val reply = final("The answer is 4.")
        assertEquals("The answer is 4.", reply.answer)
        assertEquals(true, reply.implicit)
    }

    @Test
    fun `an empty or blank reply is malformed`() {
        assertTrue(AgentParser.parse("") is AgentReply.Malformed)
        assertTrue(AgentParser.parse("  \n ``` \n") is AgentReply.Malformed)
    }

    @Test
    fun `a thought alone is malformed and keeps the thought`() {
        val reply = AgentParser.parse("Thought: let me think about it") as AgentReply.Malformed
        assertEquals("let me think about it", reply.thought)
    }

    @Test
    fun `a final answer after an action is not the end because the action was never executed`() {
        val reply = act(
            "Thought: calc\nAction: calculator\nAction Input: {\"expression\":\"1+1\"}\nObservation: 2\nFinal Answer: 2",
        )
        assertEquals("calculator", reply.tool)
        assertEquals("{\"expression\":\"1+1\"}", reply.input)
    }

    @Test
    fun `a final answer before an action ends the run`() {
        val reply = final("Final Answer: 5\nAction: calculator\nAction Input: {}")
        assertEquals("5", reply.answer)
    }

    @Test
    fun `the final answer stops at a following thought line`() {
        assertEquals("5", final("Final Answer: 5\nThought: anything else").answer)
    }

    @Test
    fun `a multi line final answer is kept whole`() {
        assertEquals("line one\n- item\n- item 2", final("Final Answer: line one\n- item\n- item 2").answer)
    }

    @Test
    fun `an empty final answer is malformed`() {
        assertTrue(AgentParser.parse("Thought: x\nFinal Answer:") is AgentReply.Malformed)
    }

    @Test
    fun `several actions in one reply take the first`() {
        val reply = act(
            "Action: calculator\nAction Input: {\"expression\":\"1+1\"}\n" +
                "Action: notes\nAction Input: {\"action\":\"read\"}",
        )
        assertEquals("calculator", reply.tool)
        assertEquals("{\"expression\":\"1+1\"}", reply.input)
    }

    @Test
    fun `a hallucinated observation after the input is dropped`() {
        val reply = act("Action: calculator\nAction Input: {\"expression\":\"3*3\"}\nObservation: 9\nThought: so 9")
        assertEquals("{\"expression\":\"3*3\"}", reply.input)
    }

    @Test
    fun `code fences around the reply are ignored`() {
        val reply = act("```\nThought: t\nAction: calculator\nAction Input: {\"expression\":\"1+1\"}\n```")
        assertEquals("calculator", reply.tool)
        assertEquals("{\"expression\":\"1+1\"}", reply.input)
    }

    @Test
    fun `a fenced json input keeps only the json`() {
        val reply = act("Action: calculator\nAction Input:\n```json\n{\"expression\": \"2*2\"}\n```")
        assertEquals("{\"expression\": \"2*2\"}", reply.input)
    }

    @Test
    fun `text before the first label is ignored`() {
        val reply = act("Sure, let me help you with that!\nAction: calculator\nAction Input: {\"expression\":\"1\"}")
        assertEquals("calculator", reply.tool)
    }

    @Test
    fun `labels are case insensitive`() {
        val reply = act("thought: x\nACTION: Calculator\naction input: {\"expression\":\"1\"}")
        assertEquals("calculator", reply.tool)
    }

    @Test
    fun `bold labels are understood`() {
        val reply = act("**Thought:** x\n**Action:** calculator\n**Action Input:** {\"expression\":\"3*3\"}")
        assertEquals("x", reply.thought)
        assertEquals("calculator", reply.tool)
        assertEquals("{\"expression\":\"3*3\"}", reply.input)
    }

    @Test
    fun `labels on one line are understood`() {
        val reply = act("Thought: x Action: calculator Action Input: {\"expression\":\"1+2\"}")
        assertEquals("x", reply.thought)
        assertEquals("calculator", reply.tool)
        assertEquals("{\"expression\":\"1+2\"}", reply.input)
    }

    @Test
    fun `spanish labels are understood`() {
        val reply = act("Pensamiento: calcular\nAcción: calculator\nEntrada de la acción: {\"expression\":\"2+2\"}")
        assertEquals("calcular", reply.thought)
        assertEquals("calculator", reply.tool)
        assertEquals("{\"expression\":\"2+2\"}", reply.input)
        assertEquals("cuatro", final("Respuesta final: cuatro").answer)
    }

    @Test
    fun `a bare Answer label at the start of a line is a final answer but not in the middle of prose`() {
        assertEquals("42", final("Answer: 42").answer)
        val prose = final("The answer: 42 is what you get")
        assertEquals(true, prose.implicit)
    }

    @Test
    fun `a call style action without an Action Input line`() {
        val parens = act("Action: calculator(\"2+2\")")
        assertEquals("calculator", parens.tool)
        assertEquals("2+2", parens.input)
        val brackets = act("Action: calculator[3*3]")
        assertEquals("3*3", brackets.input)
        val brace = act("Action: notes {\"action\":\"read\"}")
        assertEquals("notes", brace.tool)
        assertEquals("{\"action\":\"read\"}", brace.input)
    }

    @Test
    fun `an action without input has an empty input`() {
        val reply = act("Thought: x\nAction: device_info")
        assertEquals("device_info", reply.tool)
        assertEquals("", reply.input)
    }

    @Test
    fun `an action with no tool name is malformed`() {
        assertTrue(AgentParser.parse("Action:\nAction Input: {\"a\":1}") is AgentReply.Malformed)
    }

    @Test
    fun `tool names are canonicalised`() {
        assertEquals("current_time", act("Action: `Current Time`\nAction Input: UTC").tool)
        assertEquals("calculator", act("Action: Calculator.\nAction Input: 1+1").tool)
        assertEquals("unit_converter", act("Action: \"unit-converter\"\nAction Input: 1 km to m").tool)
    }

    @Test
    fun `json with trailing comma and single quotes is returned raw and still readable by the tool`() {
        val reply = act("Action: calculator\nAction Input: {'expression': '2+2',}")
        assertEquals("{'expression': '2+2',}", reply.input)
        assertEquals("2+2", ToolArgs(reply.input).text("expression"))
    }

    @Test
    fun `braces inside json strings do not end the input early`() {
        val reply = act("Action: notes\nAction Input: {\"action\": \"save\", \"text\": \"use } and { freely\"}\nObservation: x")
        assertEquals("{\"action\": \"save\", \"text\": \"use } and { freely\"}", reply.input)
    }

    @Test
    fun `a label inside a json string is not a label`() {
        val reply = act("Action: notes\nAction Input: {\"action\": \"save\", \"text\": \"Final Answer: later\"}")
        assertEquals("notes", reply.tool)
        assertEquals("{\"action\": \"save\", \"text\": \"Final Answer: later\"}", reply.input)
    }

    @Test
    fun `a truncated json input is returned as is`() {
        val reply = act("Action: calculator\nAction Input: {\"expression\": \"2+")
        assertEquals("{\"expression\": \"2+", reply.input)
    }

    @Test
    fun `a plain text input loses wrapping quotes`() {
        assertEquals("Europe/Madrid", act("Action: current_time\nAction Input: \"Europe/Madrid\"").input)
    }

    @Test
    fun `windows line endings are fine`() {
        val reply = act("Thought: x\r\nAction: calculator\r\nAction Input: {\"expression\":\"1\"}\r\n")
        assertEquals("calculator", reply.tool)
        assertEquals("{\"expression\":\"1\"}", reply.input)
    }

    @Test
    fun `a final answer with a thought has the thought`() {
        assertNull(final("Final Answer: ok").thought)
    }
}
