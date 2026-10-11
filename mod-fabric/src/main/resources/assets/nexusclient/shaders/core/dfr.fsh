#version 330
#extension GL_ARB_separate_shader_objects : require

// DFR (Dual Frame Reset) – makes the generated in-between frame.
//
// CurrentSampler  = the newest real frame
// PreviousSampler = the real frame before it
// ParamsSampler   = a tiny 2x1 picture carrying the settings (so no extra buffers are needed):
//   pixel (0,0): red = mode (0 = Smooth blend, 0.5 = Motion Blur, 1 = Low lag shift), green = strength (0-1)
//   pixel (1,0): red+green = sideways shift, blue+alpha = up/down shift (16-bit each, 0.5 = none)

uniform sampler2D CurrentSampler;
uniform sampler2D PreviousSampler;
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float decode16(float high, float low) {
    return (high * 255.0 * 256.0 + low * 255.0) / 65535.0;
}

void main() {
    vec4 settings = texelFetch(ParamsSampler, ivec2(0, 0), 0);
    vec4 shift = texelFetch(ParamsSampler, ivec2(1, 0), 0);
    float strength = settings.g;

    if (settings.r < 0.25) {
        // Smooth: halfway between the two real frames (at full strength).
        vec4 current = texture(CurrentSampler, texCoord);
        vec4 previous = texture(PreviousSampler, texCoord);
        fragColor = mix(current, previous, 0.5 * strength);
    } else if (settings.r < 0.75) {
        // Motion Blur: keep "strength" of the last picture shown, so movement leaves a soft trail.
        fragColor = mix(texture(CurrentSampler, texCoord), texture(PreviousSampler, texCoord), strength);
    } else {
        // Low lag: the newest frame, moved the way the camera is already turning.
        vec2 offset = vec2(decode16(shift.r, shift.g), decode16(shift.b, shift.a)) - 0.5;
        fragColor = texture(CurrentSampler, clamp(texCoord + offset * strength, 0.0, 1.0));
    }
    fragColor.a = 1.0;
}
