using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using AntiROTAid.Windows.Models;

namespace AntiROTAid.Windows.Services;

/// <summary>Local-only exam list, persisted as JSON under %AppData%\AntiROTAid.
/// Mirrors the Android AraPlanStore exam surface (save/upsert, attach materials).</summary>
public sealed class ExamStore
{
    private static string Dir =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "AntiROTAid");
    private static string FilePath => Path.Combine(Dir, "exams.json");

    public List<Exam> Exams { get; private set; } = new();

    public void Load()
    {
        try
        {
            if (File.Exists(FilePath))
                Exams = JsonSerializer.Deserialize<List<Exam>>(File.ReadAllText(FilePath)) ?? new();
        }
        catch { Exams = new(); }
        Sort();
    }

    public Exam? Get(string id) => Exams.FirstOrDefault(x => x.Id == id);

    public Exam Add(string name, DateTime date)
    {
        var e = new Exam { Name = string.IsNullOrWhiteSpace(name) ? I18n.T.UntitledExam : name.Trim(), Date = date };
        Exams.Add(e);
        Sort();
        Save();
        return e;
    }

    /// <summary>Insert or replace an exam by id, then persist.</summary>
    public void Save(Exam exam)
    {
        var i = Exams.FindIndex(x => x.Id == exam.Id);
        if (i >= 0) Exams[i] = exam; else Exams.Add(exam);
        Sort();
        Persist();
    }

    public void Delete(string id)
    {
        Exams.RemoveAll(x => x.Id == id);
        Persist();
    }

    public void AddMaterial(string examId, string materialId)
    {
        var e = Get(examId);
        if (e is null || e.MaterialIds.Contains(materialId)) return;
        e.MaterialIds.Add(materialId);
        Persist();
    }

    public void RemoveMaterial(string examId, string materialId)
    {
        var e = Get(examId);
        if (e is null) return;
        e.MaterialIds.Remove(materialId);
        Persist();
    }

    /// <summary>Drop a material id from EVERY exam (called when the course is deleted) so no
    /// exam is left pointing at a course that no longer exists.</summary>
    public void RemoveMaterialFromAll(string materialId)
    {
        bool changed = false;
        foreach (var e in Exams)
            if (e.MaterialIds.Remove(materialId)) changed = true;
        if (changed) Persist();
    }

    private void Sort() => Exams.Sort((a, b) => a.Date.CompareTo(b.Date));

    private void Save() => Persist();

    private void Persist()
    {
        try
        {
            AtomicFile.Write(FilePath,
                JsonSerializer.Serialize(Exams, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch { /* best-effort */ }
    }
}
