#include <gtest/gtest.h>
#include "typing/commas/infrastructure/VerifiedEdit.h"

TEST(VerifiedEdit, InsertsOnlyCommasWithoutChangingPunctuationOrWhitespace) {
    autopunct::VerifiedEdit edit(u"Известно что это город в котором я живу. ", {8, 22});
    EXPECT_EQ(edit.replacement(), u"Известно, что это город, в котором я живу. ");
    EXPECT_TRUE(edit.canUndo(edit.replacement()));
    EXPECT_FALSE(edit.canUndo(u"Новый текст"));
}

TEST(VerifiedEdit, UsesUtf16OffsetsAndKeepsEmoji) {
    autopunct::VerifiedEdit edit(u"😀 знаю что ", {7});
    EXPECT_EQ(edit.replacement(), u"😀 знаю, что ");
}

TEST(VerifiedEdit, RejectsInvalidDuplicateSurrogateAndEdgeOffsets) {
    EXPECT_THROW((autopunct::VerifiedEdit(u"да нет", {0})), std::invalid_argument);
    EXPECT_THROW((autopunct::VerifiedEdit(u"да нет", {6})), std::invalid_argument);
    EXPECT_THROW((autopunct::VerifiedEdit(u"да нет", {2, 2})), std::invalid_argument);
    EXPECT_THROW((autopunct::VerifiedEdit(u"да нет", {3, 2})), std::invalid_argument);
    EXPECT_THROW((autopunct::VerifiedEdit(u"да, нет", {2})), std::invalid_argument);
    EXPECT_THROW((autopunct::VerifiedEdit(u"😀 привет", {1})), std::invalid_argument);
    EXPECT_THROW((autopunct::VerifiedEdit(std::u16string(513, u'я'), {2})), std::invalid_argument);
}

TEST(VerifiedEdit, RejectsChangedTextRevisionFieldOrDocumentEvenWhenTextMatches) {
    autopunct::VerifiedEdit edit(u"Известно что ", {8}, "document-A", 42, 100);
    EXPECT_TRUE(edit.isCurrent(u"Известно что ", "document-A", 42, 100));
    EXPECT_FALSE(edit.isCurrent(u"Известно что уже ", "document-A", 42, 100));
    EXPECT_FALSE(edit.isCurrent(u"Известно что ", "document-A", 43, 100));
    EXPECT_FALSE(edit.isCurrent(u"Известно что ", "document-B", 42, 100));
    EXPECT_FALSE(edit.isCurrent(u"Известно что ", "document-A", 42, 101));
}
