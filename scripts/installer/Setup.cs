using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Collections.Generic;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("Import System - Instalador Windows")]
[assembly: AssemblyVersion("1.1.1.0")]
[assembly: AssemblyFileVersion("1.1.1.0")]

namespace ImportSystemInstaller {
 sealed class Slice:Stream {
  readonly Stream source;readonly long start,length;long position;
  internal Slice(Stream stream,long offset,long size){source=stream;start=offset;length=size;Position=0;}
  public override bool CanRead{get{return true;}}public override bool CanSeek{get{return true;}}public override bool CanWrite{get{return false;}}
  public override long Length{get{return length;}}
  public override long Position{get{return position;}set{if(value<0||value>length)throw new IOException("Posición fuera del paquete.");position=value;source.Position=start+value;}}
  public override int Read(byte[] buffer,int offset,int count){int n=source.Read(buffer,offset,(int)Math.Min(count,length-position));position+=n;return n;}
  public override long Seek(long offset,SeekOrigin origin){Position=(origin==SeekOrigin.Begin?0:origin==SeekOrigin.Current?position:length)+offset;return position;}
  public override void Flush(){}public override void SetLength(long value){throw new NotSupportedException();}public override void Write(byte[] buffer,int offset,int count){throw new NotSupportedException();}
 }
 static class Package {
  internal const string Version="1.1.1";
  internal const string Manifest=".import-system-files.txt";
  internal const string RegistryPath=@"Software\Microsoft\Windows\CurrentVersion\Uninstall\GestionImportaciones";
  internal static string Self { get { return Application.ExecutablePath; } }
  internal static string SafePath(string root,string relative) {
   if(String.IsNullOrWhiteSpace(relative)||Path.IsPathRooted(relative)||relative.Contains(":")) throw new IOException("Ruta de archivo no válida.");
   string full=Path.GetFullPath(Path.Combine(root,relative.Replace('/',Path.DirectorySeparatorChar)));
   string prefix=Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar)+Path.DirectorySeparatorChar;
   if(!full.StartsWith(prefix,StringComparison.OrdinalIgnoreCase)) throw new IOException("El paquete intenta salir de su carpeta.");
   return full;
  }
  internal static string ValidateDestination(string input) {
   string path=Path.GetFullPath(input).TrimEnd(Path.DirectorySeparatorChar);
   if(path.Length<8||path.Equals(Path.GetPathRoot(path).TrimEnd(Path.DirectorySeparatorChar),StringComparison.OrdinalIgnoreCase)) throw new IOException("Elige una carpeta exclusiva para Import System.");
   foreach(var special in new[]{Environment.SpecialFolder.Windows,Environment.SpecialFolder.UserProfile,Environment.SpecialFolder.MyDocuments,Environment.SpecialFolder.DesktopDirectory}) {
    string forbidden=Environment.GetFolderPath(special).TrimEnd(Path.DirectorySeparatorChar);
    if(path.Equals(forbidden,StringComparison.OrdinalIgnoreCase)||(special==Environment.SpecialFolder.Windows&&path.StartsWith(forbidden+"\\",StringComparison.OrdinalIgnoreCase))) throw new IOException("No uses una carpeta del sistema ni una carpeta personal completa.");
   }
   if(Directory.Exists(path)&&Directory.GetFileSystemEntries(path).Length>0&&!File.Exists(Path.Combine(path,Manifest))&&!File.Exists(Path.Combine(path,"GestionImportaciones.exe"))) throw new IOException("La carpeta contiene otros archivos. Elige una carpeta vacía o la instalación anterior.");
   return path;
  }
  internal static void EnsureClosed(string folder) {
   foreach(Process p in Process.GetProcessesByName("GestionImportaciones")) {
    string running=null;try { running=p.MainModule.FileName; } catch { }
    if(running!=null&&Path.GetFullPath(running).StartsWith(Path.GetFullPath(folder).TrimEnd(Path.DirectorySeparatorChar)+"\\",StringComparison.OrdinalIgnoreCase)) throw new IOException("Cierra Import System antes de actualizar o desinstalar.");
   }
  }
  internal static List<string> Install(string destination,Action<int,string> progress) {
   destination=ValidateDestination(destination);EnsureClosed(destination);
   var owned=new List<string>();
   using(var stream=File.OpenRead(Self)) {
    if(stream.Length<24) throw new IOException("El instalador está incompleto.");
    stream.Seek(-24,SeekOrigin.End);var reader=new BinaryReader(stream);
    long offset=reader.ReadInt64(),length=reader.ReadInt64();string magic=Encoding.ASCII.GetString(reader.ReadBytes(8));
    if(magic!="ISSETUP1"||offset<=0||length<=0||offset>stream.Length-24||length!=stream.Length-24-offset) throw new IOException("El instalador está dañado. Descárgalo otra vez desde GitHub.");
    using(var slice=new Slice(stream,offset,length))
    using(var zip=new ZipArchive(slice,ZipArchiveMode.Read,true)) {
     bool app=false,runtime=false;
     foreach(var e in zip.Entries) { SafePath(destination,e.FullName);app|=e.FullName=="GestionImportaciones.exe";runtime|=e.FullName=="runtime/bin/java.exe"; }
     if(!app||!runtime) throw new IOException("El paquete no contiene el programa y Java.");
     Directory.CreateDirectory(destination);int done=0;
     foreach(var entry in zip.Entries) {
      string path=SafePath(destination,entry.FullName);
      if(!String.IsNullOrEmpty(entry.Name)) {
       Directory.CreateDirectory(Path.GetDirectoryName(path));
       // Las preferencias locales existentes no se sobrescriben al actualizar.
       if(!(entry.FullName=="app/config/database.properties"&&File.Exists(path))) {
        using(var input=entry.Open()) using(var output=File.Create(path)) input.CopyTo(output);
       }
       owned.Add(entry.FullName);
      }
      progress((++done)*94/zip.Entries.Count,entry.Name);
     }
    }
    stream.Position=0;
    using(var output=File.Create(Path.Combine(destination,"Desinstalar_Import_System.exe"))) {
     byte[] buffer=new byte[65536];long remaining=offset;
     while(remaining>0) { int n=stream.Read(buffer,0,(int)Math.Min(buffer.Length,remaining));if(n==0)throw new EndOfStreamException();output.Write(buffer,0,n);remaining-=n; }
    }
   }
   owned.Add("Desinstalar_Import_System.exe");
   File.WriteAllLines(Path.Combine(destination,Manifest),owned,Encoding.UTF8);
   progress(97,"Registrando el programa");return owned;
  }
  internal static void Shortcut(string path,string target,string folder,string arguments) {
   Type t=Type.GetTypeFromProgID("WScript.Shell");if(t==null)throw new IOException("No se pudo crear el acceso directo.");
   object shell=Activator.CreateInstance(t),link=t.InvokeMember("CreateShortcut",BindingFlags.InvokeMethod,null,shell,new object[]{path});
   Type lt=link.GetType();
   foreach(var pair in new[]{new[]{"TargetPath",target},new[]{"WorkingDirectory",folder},new[]{"Arguments",arguments},new[]{"IconLocation",target+",0"}}) lt.InvokeMember(pair[0],BindingFlags.SetProperty,null,link,new object[]{pair[1]});
   lt.InvokeMember("Save",BindingFlags.InvokeMethod,null,link,null);
  }
  internal static void Register(string folder,bool desktop) {
   string exe=Path.Combine(folder,"GestionImportaciones.exe"),uninstall=Path.Combine(folder,"Desinstalar_Import_System.exe");
   if(desktop)Shortcut(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),"Import System.lnk"),exe,folder,"");
   string menu=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs),"Import System");Directory.CreateDirectory(menu);
   Shortcut(Path.Combine(menu,"Import System.lnk"),exe,folder,"");Shortcut(Path.Combine(menu,"Desinstalar.lnk"),uninstall,folder,"/uninstall");
   using(var key=Registry.CurrentUser.CreateSubKey(RegistryPath)) {
    key.SetValue("DisplayName","Import System");key.SetValue("DisplayVersion",Version);key.SetValue("Publisher","Equipo DBQP · RPLC · ERQZ");key.SetValue("InstallLocation",folder);key.SetValue("DisplayIcon",exe);key.SetValue("UninstallString","\""+uninstall+"\" /uninstall");key.SetValue("NoModify",1);key.SetValue("NoRepair",1);
   }
  }
  internal static string DefaultFolder() {
   using(var key=Registry.CurrentUser.OpenSubKey(RegistryPath)) {
    string old=key==null?null:key.GetValue("InstallLocation") as string;
    if(!String.IsNullOrEmpty(old)&&File.Exists(Path.Combine(old,"GestionImportaciones.exe")))return old;
   }
   return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","GestionImportaciones");
  }
  internal static void Uninstall() {
   string folder=Path.GetDirectoryName(Self),manifest=Path.Combine(folder,Manifest);
   if(!File.Exists(manifest)||!File.Exists(Path.Combine(folder,"GestionImportaciones.exe"))) throw new IOException("No se encontró una instalación válida. No se eliminó ningún archivo.");
   EnsureClosed(folder);
   if(MessageBox.Show("Se quitará Import System.\n\nTus datos de SQLite y documentos personales se conservarán.\n¿Continuar?","Desinstalar Import System",MessageBoxButtons.YesNo,MessageBoxIcon.Question)!=DialogResult.Yes)return;
   var directories=new HashSet<string>();
   foreach(string relative in File.ReadAllLines(manifest,Encoding.UTF8)) {
    string file=SafePath(folder,relative);if(String.Equals(file,Self,StringComparison.OrdinalIgnoreCase))continue;
    if(File.Exists(file))File.Delete(file);
    string dir=Path.GetDirectoryName(file);
    while(dir.Length>folder.Length) { directories.Add(dir);dir=Path.GetDirectoryName(dir); }
   }
   var sorted=new List<string>(directories);sorted.Sort((a,b)=>b.Length.CompareTo(a.Length));
   foreach(var dir in sorted)if(Directory.Exists(dir)&&Directory.GetFileSystemEntries(dir).Length==0)Directory.Delete(dir);
   string desktop=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory),"Import System.lnk");if(File.Exists(desktop))File.Delete(desktop);
   string menu=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs),"Import System");
   foreach(string name in new[]{"Import System.lnk","Desinstalar.lnk"}) { string file=Path.Combine(menu,name);if(File.Exists(file))File.Delete(file); }
   if(Directory.Exists(menu)&&Directory.GetFileSystemEntries(menu).Length==0)Directory.Delete(menu);
   using(var key=Registry.CurrentUser.OpenSubKey(RegistryPath)) {
    if(key!=null&&!String.Equals(key.GetValue("InstallLocation") as string,folder,StringComparison.OrdinalIgnoreCase)) throw new IOException("El registro pertenece a otra instalación.");
   }
   Registry.CurrentUser.DeleteSubKeyTree(RegistryPath,false);
   MessageBox.Show("Programa desinstalado. Tus datos se conservaron.\nPuedes borrar esta carpeta si ya no la necesitas.","Import System",MessageBoxButtons.OK,MessageBoxIcon.Information);
   // Conserva sólo este pequeño desinstalador y el manifiesto, sin borrar carpetas ajenas.
  }
 }

 sealed class SetupForm:Form {
  Panel body=new Panel();Label subtitle=new Label();Button next=new Button(),back=new Button(),cancel=new Button();
  TextBox folder=new TextBox();CheckBox desktop=new CheckBox(),launch=new CheckBox();ProgressBar bar=new ProgressBar();Label status=new Label();int step=0;bool busy=false,finished=false;
  internal SetupForm() {
   Text="Instalar Import System "+Package.Version;ClientSize=new Size(700,470);Font=new Font("Segoe UI",10);BackColor=Color.White;StartPosition=FormStartPosition.CenterScreen;FormBorderStyle=FormBorderStyle.FixedDialog;MaximizeBox=false;AutoScaleMode=AutoScaleMode.Dpi;
   try { Icon=Icon.ExtractAssociatedIcon(Package.Self); }catch{}
   var header=new Panel{Bounds=new Rectangle(0,0,700,105),BackColor=Color.FromArgb(21,83,219)};
   header.Controls.Add(new Label{Text="IS",Font=new Font("Segoe UI",23,FontStyle.Bold),ForeColor=Color.White,Bounds=new Rectangle(25,25,60,50)});
   header.Controls.Add(new Label{Text="Import System",Font=new Font("Segoe UI",22,FontStyle.Bold),ForeColor=Color.White,Bounds=new Rectangle(100,15,570,45)});
   subtitle.Bounds=new Rectangle(102,65,570,25);subtitle.ForeColor=Color.White;header.Controls.Add(subtitle);Controls.Add(header);
   body.Bounds=new Rectangle(30,125,640,255);Controls.Add(body);
   var footer=new Panel{Bounds=new Rectangle(0,400,700,70),BackColor=Color.FromArgb(242,246,251)};
   back.Text="Anterior";back.Bounds=new Rectangle(335,17,100,36);back.Click+=(s,e)=>ShowStep(Math.Max(0,step-1));
   next.Bounds=new Rectangle(445,17,110,36);next.BackColor=Color.FromArgb(21,83,219);next.ForeColor=Color.White;next.FlatStyle=FlatStyle.Flat;next.Click+=Next;
   cancel.Text="Cancelar";cancel.Bounds=new Rectangle(565,17,110,36);cancel.Click+=(s,e)=>Close();footer.Controls.AddRange(new Control[]{back,next,cancel});Controls.Add(footer);
   folder.Text=Package.DefaultFolder();desktop.Checked=true;launch.Checked=true;
   FormClosing+=(s,e)=>{if(busy)e.Cancel=true;};ShowStep(0);
  }
  Label TextLabel(string text,int y,int height,bool title=false) {
   return new Label{Text=text,Bounds=new Rectangle(0,y,640,height),ForeColor=Color.FromArgb(21,43,67),Font=new Font("Segoe UI",title?15:10,title?FontStyle.Bold:FontStyle.Regular)};
  }
  internal void ShowStep(int value) {
   step=value;body.Controls.Clear();back.Enabled=value==1;next.Enabled=true;
   if(value==0) {
    subtitle.Text="Bienvenido al asistente de instalación para Windows";next.Text="Siguiente";
    body.Controls.Add(TextLabel("Todo listo para trabajar en tu computadora",0,40,true));
    body.Controls.Add(TextLabel("Ventas y caja · Inventario y ubicaciones · Compras y DUA\nReportes PDF y Excel · Motos y expedientes internos SUNARP\n\nIncluye Java y SQLite. No necesitas instalar una base de datos\nni configurar servidores. Tus datos se guardan localmente.\n\nEste asistente instala el programa para tu usuario de Windows.",53,190));
   } else if(value==1) {
    subtitle.Text="Elige la carpeta y pulsa Instalar";next.Text="Instalar";
    body.Controls.Add(TextLabel("¿Dónde quieres instalar Import System?",0,40,true));body.Controls.Add(TextLabel("Carpeta del programa",53,25));
    folder.Bounds=new Rectangle(0,84,515,30);body.Controls.Add(folder);
    var browse=new Button{Text="Examinar...",Bounds=new Rectangle(525,81,110,34)};
    browse.Click+=(s,e)=>{using(var dlg=new FolderBrowserDialog{Description="Elige una carpeta exclusiva para Import System"})if(dlg.ShowDialog()==DialogResult.OK)folder.Text=Path.Combine(dlg.SelectedPath,"GestionImportaciones");};body.Controls.Add(browse);
    desktop.Text="Crear acceso directo en el Escritorio";desktop.Bounds=new Rectangle(0,133,630,30);body.Controls.Add(desktop);
    body.Controls.Add(TextLabel("Se añadirá al Menú Inicio y a Aplicaciones de Windows.\nLa actualización conserva tu base de datos y tus documentos.",180,65));
   } else {
    subtitle.Text="Copiando el programa y sus componentes";next.Enabled=false;back.Enabled=false;cancel.Enabled=false;
    body.Controls.Add(TextLabel("Instalando Import System",0,40,true));bar.Bounds=new Rectangle(0,68,635,25);bar.Value=0;body.Controls.Add(bar);status.Bounds=new Rectangle(0,115,635,65);body.Controls.Add(status);
    launch.Text="Abrir Import System al finalizar";launch.Bounds=new Rectangle(0,195,635,35);launch.Visible=false;body.Controls.Add(launch);
   }
  }
  async void Next(object sender,EventArgs e) {
   if(finished) { if(launch.Checked)Process.Start(new ProcessStartInfo(Path.Combine(folder.Text,"GestionImportaciones.exe")){WorkingDirectory=folder.Text,UseShellExecute=true});Close();return; }
   if(step==0){ShowStep(1);return;}
   try {
    string dest=Package.ValidateDestination(folder.Text);folder.Text=dest;bool shortcut=desktop.Checked;ShowStep(2);busy=true;
    await Task.Run(()=>{Package.Install(dest,(pct,name)=>Invoke((Action)(()=>{bar.Value=pct;status.Text=name;})));Package.Register(dest,shortcut);});
    bar.Value=100;status.Text="Instalación completada.\nYa puedes abrir el programa desde el Escritorio o el Menú Inicio.";subtitle.Text="Import System se instaló correctamente";finished=true;next.Text="Finalizar";next.Enabled=true;launch.Visible=true;cancel.Text="Cerrar";cancel.Enabled=true;
   }catch(Exception ex){MessageBox.Show(ex.Message,"No se completó la instalación",MessageBoxButtons.OK,MessageBoxIcon.Error);ShowStep(1);cancel.Enabled=true;}
   finally{busy=false;}
  }
 }

 static class Program {
  [STAThread] static int Main(string[] args) {
   Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);
   try {
    if(args.Length==2&&args[0]=="/verify") {
     Package.Install(args[1],(pct,name)=>{});
     bool rejected=false;try{Package.SafePath(args[1],"../escape");}catch(IOException){rejected=true;}if(!rejected)throw new Exception("Falló el control de rutas.");
     File.WriteAllText(Path.Combine(args[1],"VERIFICADO.txt"),"Paquete extraído; rutas comprobadas; sin registro ni accesos directos.");return 0;
    }
    if(args.Length==2&&args[0]=="/preview") {
     Directory.CreateDirectory(args[1]);using(var form=new SetupForm()) {form.Show();Application.DoEvents();for(int i=0;i<2;i++){form.ShowStep(i);Application.DoEvents();using(var bmp=new Bitmap(form.Width,form.Height)){form.DrawToBitmap(bmp,new Rectangle(Point.Empty,form.Size));bmp.Save(Path.Combine(args[1],"instalador-"+i+".png"));}}}return 0;
    }
    if((args.Length>0&&args[0]=="/uninstall")||Path.GetFileName(Package.Self).StartsWith("Desinstalar",StringComparison.OrdinalIgnoreCase)){Package.Uninstall();return 0;}
    Application.Run(new SetupForm());return 0;
   }catch(Exception ex){if(args.Length>0)File.WriteAllText(Path.Combine(Path.GetTempPath(),"ImportSystem-setup-error.txt"),ex.ToString());else MessageBox.Show(ex.Message,"Import System",MessageBoxButtons.OK,MessageBoxIcon.Error);return 1;}
  }
 }
}
