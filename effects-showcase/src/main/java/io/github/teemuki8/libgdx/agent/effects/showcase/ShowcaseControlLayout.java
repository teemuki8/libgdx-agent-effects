package io.github.teemuki8.libgdx.agent.effects.showcase;

record ShowcaseControlLayout(float timeLabelX, float timeSliderX, float timeValueX,
        float intensityLabelX, float intensitySliderX, float intensityValueX) {

    static ShowcaseControlLayout at(float leftX) {
        return new ShowcaseControlLayout(leftX, leftX + 60f, leftX + 258f,
            leftX + 330f, leftX + 425f, leftX + 623f);
    }
}
