package android.util
import org.xmlpull.v1.XmlPullParserFactory
object Xml { fun newPullParser() = XmlPullParserFactory.newInstance().newPullParser() }
