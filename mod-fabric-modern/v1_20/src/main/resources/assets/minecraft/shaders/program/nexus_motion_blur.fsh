#version 150

// Nexus Motion Blur (1.20.x): keeps "BlurFactor" of the last picture shown, so fast camera turns
// leave a soft trail. (Nexus picks one of the nexus_motion_blur_<strength>.json effects.)

uniform sampler2D DiffuseSampler;
uniform sampler2D PrevSampler;
uniform float BlurFactor;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 current = texture(DiffuseSampler, texCoord).rgb;
    vec3 previous = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(current, previous, BlurFactor), 1.0);
}
