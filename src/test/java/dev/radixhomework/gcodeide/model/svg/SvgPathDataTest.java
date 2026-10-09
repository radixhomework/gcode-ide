package dev.radixhomework.gcodeide.model.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SvgPathDataTest {

    private static List<Character> letters(String d) {
        return SvgPathData.parse(d).stream().map(c -> c.letter()).toList();
    }

    @Test
    void absoluteCommands() {
        var cmds = SvgPathData.parse("M 10 20 L 30 40 H 50 V 60 C 1 2 3 4 5 6 S 7 8 9 10 Q 11 12 13 14 T 15 16 A 5 5 0 0 1 20 20 Z");
        assertEquals('M', cmds.get(0).letter());
        assertEquals(2, cmds.get(0).args().length);
        assertEquals(6, cmds.get(4).args().length); // C
        assertEquals(7, cmds.get(9).args().length); // A
        // "S 7 8 9 10" is two implicit S commands (SVG implicit repeat)
        assertEquals(2, cmds.get(5).args().length);
        assertEquals(2, cmds.get(6).args().length);
        assertEquals('Z', cmds.get(cmds.size() - 1).letter());
        assertTrue(cmds.stream().noneMatch(SvgPathData.Cmd::relative));
        assertEquals(11, cmds.size()); // S repeats implicitly: M L H V C S S Q T A Z
    }

    @Test
    void relativeCommandsKeepCase() {
        var cmds = SvgPathData.parse("m 10 10 l 5 0 c 1 1 2 2 3 3 z");
        assertTrue(cmds.get(0).relative());
        assertTrue(cmds.get(1).relative());
        assertTrue(cmds.get(2).relative());
        assertTrue(cmds.get(3).relative());
    }

    @Test
    void implicitMovetoContinuationBecomesLineto() {
        assertEquals(List.of('M', 'L', 'L'), letters("M 0 0 10 10 20 20"));
        // absolute moveto -> absolute implicit lineto
        assertEquals(20.0, SvgPathData.parse("M 0 0 10 10 20 20").get(2).args()[0]);
        // relative moveto -> relative implicit lineto
        var rel = SvgPathData.parse("m 0 0 10 10");
        assertTrue(rel.get(1).relative());
        assertEquals(10.0, rel.get(1).args()[0]);
    }

    @Test
    void hAndVTakeSingleArgument() {
        var cmds = SvgPathData.parse("M 0 0 H 10 20 V 5 6");
        assertEquals(List.of('M', 'H', 'H', 'V', 'V'), letters("M 0 0 H 10 20 V 5 6"));
        assertEquals(10.0, cmds.get(1).args()[0]);
        assertEquals(20.0, cmds.get(2).args()[0]);
    }

    @Test
    void arcGroupsComplete() {
        var cmds = SvgPathData.parse("M 0 0 A 5 5 0 0 1 10 10 A 5 5 0 1 0 0 0");
        assertEquals(7, cmds.get(1).args().length);
        assertEquals(5.0, cmds.get(1).args()[0]);
        assertEquals(0.0, cmds.get(1).args()[3]); // large-arc flag
        assertEquals(1.0, cmds.get(1).args()[4]); // sweep flag
        assertEquals(1.0, cmds.get(2).args()[3]); // large-arc on second arc
    }

    @Test
    void exponentsParse() {
        var cmds = SvgPathData.parse("M 1e2 2E-1 L 1.5E+1 0");
        assertEquals(100.0, cmds.get(0).args()[0]);
        assertEquals(0.2, cmds.get(0).args()[1], 1e-12);
        assertEquals(15.0, cmds.get(1).args()[0]);
    }

    @Test
    void badSyntaxThrows() {
        assertThrows(IllegalArgumentException.class, () -> SvgPathData.parse("10 20"));
        assertThrows(IllegalArgumentException.class, () -> SvgPathData.parse("M 0"));
        assertThrows(IllegalArgumentException.class, () -> SvgPathData.parse("M 0 0 X 5"));
        assertThrows(IllegalArgumentException.class, () -> SvgPathData.parse("M 0 0 Q 1 1"));
    }
}
