package one.dugon.mediasoup_android_sdk.sdp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class H264ProfileLevelId {

    public enum Profile {
        ConstrainedBaseline(1),
        Baseline(2),
        Main(3),
        ConstrainedHigh(4),
        High(5),
        PredictiveHigh444(6);

        private final int value;

        Profile(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum Level {
        L1_b(0),
        L1(10),
        L1_1(11),
        L1_2(12),
        L1_3(13),
        L2(20),
        L2_1(21),
        L2_2(22),
        L3(30),
        L3_1(31),
        L3_2(32),
        L4(40),
        L4_1(41),
        L4_2(42),
        L5(50),
        L5_1(51),
        L5_2(52);

        private final int value;

        Level(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Level fromValue(int value) {
            for (Level level : Level.values()) {
                if (level.getValue() == value) {
                    return level;
                }
            }
            throw new IllegalArgumentException("Unknown value: " + value);
        }
    }

    public static class ProfileLevelId {
        public final Profile profile;
        public final Level level;

        public ProfileLevelId(Profile profile, Level level) {
            this.profile = profile;
            this.level = level;
        }
    }

    private static final ProfileLevelId DefaultProfileLevelId = new ProfileLevelId(Profile.ConstrainedBaseline, Level.L3_1);

    public static class BitPattern {
        public final int mask;
        public final int maskedValue;

        public BitPattern(String str) {
            this.mask = ~byteMaskString('x', str);
            this.maskedValue = byteMaskString('1', str);
        }

        public boolean isMatch(int value) {
            return this.maskedValue == (value & this.mask);
        }
    }

    public static class ProfilePattern {
        public final int profileIdc;
        public final BitPattern profileIop;
        public final Profile profile;

        public ProfilePattern(int profileIdc, BitPattern profileIop, Profile profile) {
            this.profileIdc = profileIdc;
            this.profileIop = profileIop;
            this.profile = profile;
        }
    }

    private static final List<ProfilePattern> ProfilePatterns = new ArrayList<>();

    static {
        ProfilePatterns.add(new ProfilePattern(0x42, new BitPattern("x1xx0000"), Profile.ConstrainedBaseline));
        ProfilePatterns.add(new ProfilePattern(0x4D, new BitPattern("1xxx0000"), Profile.ConstrainedBaseline));
        ProfilePatterns.add(new ProfilePattern(0x58, new BitPattern("11xx0000"), Profile.ConstrainedBaseline));
        ProfilePatterns.add(new ProfilePattern(0x42, new BitPattern("x0xx0000"), Profile.Baseline));
        ProfilePatterns.add(new ProfilePattern(0x58, new BitPattern("10xx0000"), Profile.Baseline));
        ProfilePatterns.add(new ProfilePattern(0x4D, new BitPattern("0x0x0000"), Profile.Main));
        ProfilePatterns.add(new ProfilePattern(0x64, new BitPattern("00000000"), Profile.High));
        ProfilePatterns.add(new ProfilePattern(0x64, new BitPattern("00001100"), Profile.ConstrainedHigh));
        ProfilePatterns.add(new ProfilePattern(0xF4, new BitPattern("00000000"), Profile.PredictiveHigh444));
    }

    public static ProfileLevelId parseProfileLevelId(String str) {
        final int ConstraintSet3Flag = 0x10;

        if (str == null || str.length() != 6) {
            return null;
        }

        int profileLevelIdNumeric;
        try {
            profileLevelIdNumeric = Integer.parseInt(str, 16);
        } catch (NumberFormatException e) {
            return null;
        }

        if (profileLevelIdNumeric == 0) {
            return null;
        }


        Level levelIdc = Level.fromValue(profileLevelIdNumeric & 0xFF);
        int profileIop = (profileLevelIdNumeric >> 8) & 0xFF;
        int profileIdc = (profileLevelIdNumeric >> 16) & 0xFF;

        Level level;
        switch (levelIdc) {
            case L1_1:
                level = (profileIop & ConstraintSet3Flag) != 0 ? Level.L1_b : Level.L1_1;
                break;
            case L1:
            case L1_2:
            case L1_3:
            case L2:
            case L2_1:
            case L2_2:
            case L3:
            case L3_1:
            case L3_2:
            case L4:
            case L4_1:
            case L4_2:
            case L5:
            case L5_1:
            case L5_2:
                level = levelIdc;
                break;
            default:
                return null;
        }

        for (ProfilePattern pattern : ProfilePatterns) {
            if (profileIdc == pattern.profileIdc && pattern.profileIop.isMatch(profileIop)) {
                return new ProfileLevelId(pattern.profile, level);
            }
        }

        return null;
    }

    public static String profileLevelIdToString(ProfileLevelId profileLevelId) {
        if (profileLevelId.level == Level.L1_b) {
            switch (profileLevelId.profile) {
                case ConstrainedBaseline:
                    return "42f00b";
                case Baseline:
                    return "42100b";
                case Main:
                    return "4d100b";
                default:
                    return null;
            }
        }

        String profileIdcIopString;
        switch (profileLevelId.profile) {
            case ConstrainedBaseline:
                profileIdcIopString = "42e0";
                break;
            case Baseline:
                profileIdcIopString = "4200";
                break;
            case Main:
                profileIdcIopString = "4d00";
                break;
            case ConstrainedHigh:
                profileIdcIopString = "640c";
                break;
            case High:
                profileIdcIopString = "6400";
                break;
            case PredictiveHigh444:
                profileIdcIopString = "f400";
                break;
            default:
                return null;
        }

        String levelStr = Integer.toHexString(profileLevelId.level.getValue());
        if (levelStr.length() == 1) {
            levelStr = "0" + levelStr;
        }

        return profileIdcIopString + levelStr;
    }

    public static String profileToString(Profile profile) {
        switch (profile) {
            case ConstrainedBaseline:
                return "ConstrainedBaseline";
            case Baseline:
                return "Baseline";
            case Main:
                return "Main";
            case ConstrainedHigh:
                return "ConstrainedHigh";
            case High:
                return "High";
            case PredictiveHigh444:
                return "PredictiveHigh444";
            default:
                return null;
        }
    }

    public static String levelToString(Level level) {
        switch (level) {
            case L1_b:
                return "1b";
            case L1:
                return "1";
            case L1_1:
                return "1.1";
            case L1_2:
                return "1.2";
            case L1_3:
                return "1.3";
            case L2:
                return "2";
            case L2_1:
                return "2.1";
            case L2_2:
                return "2.2";
            case L3:
                return "3";
            case L3_1:
                return "3.1";
            case L3_2:
                return "3.2";
            case L4:
                return "4";
            case L4_1:
                return "4.1";
            case L4_2:
                return "4.2";
            case L5:
                return "5";
            case L5_1:
                return "5.1";
            case L5_2:
                return "5.2";
            default:
                return null;
        }
    }

    public static ProfileLevelId parseSdpProfileLevelId(Map<String, String> params) {
        String profileLevelId = params.get("profile-level-id");
        return profileLevelId != null ? parseProfileLevelId(profileLevelId) : DefaultProfileLevelId;
    }

    public static boolean isSameProfile(Map<String, String> params1, Map<String, String> params2) {
        ProfileLevelId profileLevelId1 = parseSdpProfileLevelId(params1);
        ProfileLevelId profileLevelId2 = parseSdpProfileLevelId(params2);

        return profileLevelId1 != null && profileLevelId2 != null && profileLevelId1.profile == profileLevelId2.profile;
    }

    public static String generateProfileLevelIdStringForAnswer(Map<String, String> localSupportedParams, Map<String, String> remoteOfferedParams) {
        if (!localSupportedParams.containsKey("profile-level-id") && !remoteOfferedParams.containsKey("profile-level-id")) {
            return null;
        }

        ProfileLevelId localProfileLevelId = parseSdpProfileLevelId(localSupportedParams);
        ProfileLevelId remoteProfileLevelId = parseSdpProfileLevelId(remoteOfferedParams);

        if (localProfileLevelId == null) {
            throw new IllegalArgumentException("invalid local_profile_level_id");
        }

        if (remoteProfileLevelId == null) {
            throw new IllegalArgumentException("invalid remote_profile_level_id");
        }

        if (localProfileLevelId.profile != remoteProfileLevelId.profile) {
            throw new IllegalArgumentException("H264 Profile mismatch");
        }

        boolean levelAsymmetryAllowed = isLevelAsymmetryAllowed(localSupportedParams) && isLevelAsymmetryAllowed(remoteOfferedParams);

        Level localLevel = localProfileLevelId.level;
        Level remoteLevel = remoteProfileLevelId.level;
        Level minLevel = minLevel(localLevel, remoteLevel);

        Level answerLevel = levelAsymmetryAllowed ? localLevel : minLevel;


        return profileLevelIdToString(new ProfileLevelId(localProfileLevelId.profile, answerLevel));
    }

    private static int byteMaskString(char c, String str) {
        return ((str.charAt(0) == c ? 1 : 0) << 7) |
                ((str.charAt(1) == c ? 1 : 0) << 6) |
                ((str.charAt(2) == c ? 1 : 0) << 5) |
                ((str.charAt(3) == c ? 1 : 0) << 4) |
                ((str.charAt(4) == c ? 1 : 0) << 3) |
                ((str.charAt(5) == c ? 1 : 0) << 2) |
                ((str.charAt(6) == c ? 1 : 0) << 1) |
                ((str.charAt(7) == c ? 1 : 0));
    }

    private static boolean isLessLevel(Level a, Level b) {
        if (a == Level.L1_b) {
            return b != Level.L1 && b != Level.L1_b;
        }

        if (b == Level.L1_b) {
            return a != Level.L1;
        }

        return a.getValue() < b.getValue();
    }

    private static Level minLevel(Level a, Level b) {
        return isLessLevel(a, b) ? a : b;
    }

    private static boolean isLevelAsymmetryAllowed(Map<String, String> params) {
        String levelAsymmetryAllowed = params.get("level-asymmetry-allowed");
        return "true".equals(levelAsymmetryAllowed) || "1".equals(levelAsymmetryAllowed);
    }

}