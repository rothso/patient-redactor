package com.github.rothso.mass

import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.cos.COSString
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.pdfwriter.ContentStreamWriter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDStream
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.text.PDFTextStripperByArea
import java.awt.Rectangle
import java.io.File
import java.util.*

data class Patient(val firstName: String, val lastName: String)
data class Glyph(val unicode: String, val raw: COSString)

/**
 * The TextVisitor accepts every operator/operand pair in the PDF document and keeps track
 * of those which represent a character of text. It stores each decoded character alongside
 * a reference to its unique underlying COSString representation so that the character can
 * later be changed or replaced in the PDF.
 */
class TextVisitor(private val currentPage: PDPage) {
  private lateinit var currentFont: PDFont
  private val _buffer: MutableList<Glyph> = mutableListOf()
  val buffer: List<Glyph>
    get() = _buffer

  fun visit(operator: Operator, operands: MutableList<COSBase>) {
    // Tf and Tj are two PDF operators. Tf sets the font and Tj displays a character
    when (operator.name) {
      "Tf" -> {
        val fontName = operands[0] as COSName
        currentFont = currentPage.resources.getFont(fontName)
      }
      "Tj" -> {
        operands.forEach {
          val string = it as COSString
          val unicode = currentFont.toUnicode(string.toHexString().toInt(16))
          assert(unicode.length == 1) // must always be true
          _buffer.add(Glyph(unicode, string))
        }
      }
    }
  }
}

class PatientRedactor(private val fileName: String) {
  private val document = PDDocument.load(File(fileName))

  fun process() {
    val headerLine = getHeaderText()
    val (firstName, lastName) = getPatientName(headerLine)

    // List of strings to search for and redact. The order matters! Strings are matched
    // all-or-nothing, so we don't want to redact the patient's name before we redact the
    // header line, or else the header line won't be matched.
    val blacklist = arrayOf(
        headerLine,
        "$firstName $lastName",
        "$lastName, $firstName"
    )

    // Update the PDF tokens
    redact(blacklist)

    // Save to the output destination
    document.save("redacted-$fileName")
    document.close()
  }

  /**
   * Extract the header line (name, ID, dob) from the top of the first page.
   */
  private fun getHeaderText(): String {
    return PDFTextStripperByArea().apply {
      sortByPosition = true
      addRegion("header", Rectangle(0, 40, 800, 20))
      extractRegions(document.getPage(0))
    }.run {
      getTextForRegion("header")
    }
  }

  /**
   * Extract the patient's name (lastName, firstName) from the header.
   */
  private fun getPatientName(headerLine: String): Patient {
    val patient = headerLine.split(" (")[0]
    val names = patient.split(", ")
    return Patient(firstName = names[1], lastName = names[0])
  }

  /**
   * Replace all occurrences of the blacklisted strings, in order, with an empty string.
   */
  private fun redact(blacklist: Array<String>) {
    document.pages.forEach { page ->
      val visitor = TextVisitor(page)

      var arguments: MutableList<COSBase> = ArrayList()
      val parser = PDFStreamParser(page).apply { parse() }

      val tokens = parser.tokens
      tokens.forEach { token ->
        when (token) {
          is COSObject -> arguments.add(token.getObject())
          is Operator -> {
            // Build and store text tokens
            visitor.visit(token, arguments)
            arguments = ArrayList()
          }
          else -> arguments.add(token as COSBase)
        }
      }

      // Join the resulting buffer into a string that we can easily search
      val body: String = with(StringBuffer()) {
        visitor.buffer.map { it.unicode }.forEach { append(it) }
        toString()
      }

      // Find and remove all occurrences of each blacklisted string (case-insensitive)
      blacklist.forEach { phrase ->
        val matches = Regex(Regex.escape(phrase), RegexOption.IGNORE_CASE).findAll(body)
        matches.map { it.range }.forEach { range ->
          visitor.buffer.subList(range.start, range.endInclusive + 1).forEach { glyph ->
            glyph.raw.setValue(byteArrayOf()) // assign the empty "" string
          }
        }
      }

      // Now that the tokens are updated, replace the page's content stream
      val updatedStream = PDStream(document)
      val out = updatedStream.createOutputStream()
      ContentStreamWriter(out).writeTokens(tokens)
      page.setContents(updatedStream)
      out.close()
    }
  }
}

fun main(args: Array<String>) {
  // Performance tweak for JDK 8+
  System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider")
  PatientRedactor(args[0]).process()
}