using System;
using System.Drawing;
using System.Windows;
using Forms = System.Windows.Forms;

namespace AntiROTAid.Windows;

/// <summary>
/// System-tray presence: Start Mode / Stop Mode / Show App / Exit, plus the ARA icon.
/// Uses the WinForms NotifyIcon (the standard tray API); no Forms UI is shown.
/// </summary>
public sealed class TrayIcon : IDisposable
{
    private readonly Forms.NotifyIcon _icon;
    private readonly MainWindow _window;
    private readonly ModeController _controller;
    private readonly Forms.ToolStripMenuItem _startItem;
    private readonly Forms.ToolStripMenuItem _stopItem;

    public TrayIcon(MainWindow window, ModeController controller)
    {
        _window = window;
        _controller = controller;

        var menu = new Forms.ContextMenuStrip();
        _startItem = new Forms.ToolStripMenuItem(I18n.T.TrayStart, null, (_, _) => _controller.Start());
        _stopItem = new Forms.ToolStripMenuItem(I18n.T.TrayStop, null, (_, _) => _controller.Stop());
        var showItem = new Forms.ToolStripMenuItem(I18n.T.TrayShow, null, (_, _) => ShowWindow());
        var exitItem = new Forms.ToolStripMenuItem(I18n.T.TrayExit, null, (_, _) => ExitApp());
        menu.Items.AddRange(new Forms.ToolStripItem[]
        {
            _startItem, _stopItem, new Forms.ToolStripSeparator(), showItem, exitItem,
        });

        _icon = new Forms.NotifyIcon
        {
            Text = "AntiROTAid",
            Visible = true,
            Icon = LoadIcon(),
            ContextMenuStrip = menu,
        };
        _icon.DoubleClick += (_, _) => ShowWindow();

        _controller.StateChanged += SyncMenu;
        SyncMenu();
    }

    private void SyncMenu()
    {
        _startItem.Enabled = !_controller.IsActive;
        _stopItem.Enabled = _controller.IsActive;
    }

    private void ShowWindow()
    {
        _window.Show();
        _window.WindowState = WindowState.Normal;
        _window.Activate();
    }

    private void ExitApp()
    {
        _window.ReallyExit = true;
        Application.Current.Shutdown();
    }

    private static Icon LoadIcon()
    {
        try
        {
            var uri = new Uri("pack://application:,,,/Assets/ara_icon.png");
            using var stream = Application.GetResourceStream(uri)!.Stream;
            using var bmp = new Bitmap(stream);
            return Icon.FromHandle(bmp.GetHicon());
        }
        catch
        {
            return SystemIcons.Application;
        }
    }

    public void Dispose()
    {
        _controller.StateChanged -= SyncMenu;
        _icon.Visible = false;
        _icon.Dispose();
    }
}
