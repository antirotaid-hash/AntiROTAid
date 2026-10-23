using System;
using System.Collections.Generic;
using System.Linq;

namespace AntiROTAid.Windows.Services;

/// <summary>
/// The real "auto-delete after deadline" purge the Settings toggle promises: when an exam's
/// date has passed it is deleted, and a (user-created) course that is thereby left in NO exam
/// is deleted too. A course still attached to another not-yet-passed exam stays; a course that
/// was never attached to any exam is never touched. Demo courses are left alone (they are
/// sample content that always exists). Runs at startup while the toggle is on and from the
/// Settings "Delete now" confirmation.
/// </summary>
public static class AutoDeleteService
{
    /// <summary>Run the purge. Returns how many items (exams + courses) were removed.</summary>
    public static int Run(CourseStore courses, ExamStore exams)
    {
        var now = DateTime.Now;

        // Courses attached to ANY exam right now (so we can tell "was assigned, now orphaned"
        // apart from "never had an exam" — the latter must survive).
        var assignedBefore = new HashSet<string>(exams.Exams.SelectMany(e => e.MaterialIds));

        // 1. Delete every exam whose date has passed.
        var pastExamIds = exams.Exams.Where(e => e.Date < now).Select(e => e.Id).ToList();
        foreach (var id in pastExamIds) exams.Delete(id);

        // 2. A course that was attached to an exam but is now attached to none becomes an
        //    orphan and is deleted (only user courses; demos always exist).
        var assignedAfter = new HashSet<string>(exams.Exams.SelectMany(e => e.MaterialIds));
        int removedCourses = 0;
        foreach (var cid in assignedBefore)
        {
            if (assignedAfter.Contains(cid)) continue;      // still in a live exam → keep
            var c = courses.Get(cid);
            if (c is null || c.IsDemo) continue;            // never delete demos / already gone
            courses.Delete(cid);                            // also removes its file + exam refs
            removedCourses++;
        }
        return pastExamIds.Count + removedCourses;
    }
}
