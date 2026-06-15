import unittest
from collections import Counter

from scripts.evaluate_v2_approved_ollama_e2e import select_course_questions


class CourseQuestionShadowTest(unittest.TestCase):
    def test_selects_ten_questions_per_course(self):
        questions = select_course_questions()
        self.assertEqual(len(questions), 50)
        self.assertEqual(Counter(question.category for question in questions), {
            "java": 10,
            "spring": 10,
            "frontend": 10,
            "python": 10,
            "algorithm": 10,
        })


if __name__ == "__main__":
    unittest.main()
