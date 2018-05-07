var docutils = (function () {
  "use strict";

  // Magic key: if schema contains "_selected" radioGroup,
  // user can select only one of the schemas named in "_selected" group
  var SELECT_ONE_OF_GROUP_KEY = "_selected";

  function accordionText(fields, data) {
    return _(fields)
      .map( function( field ) {
        return lupapisteApp.services.accordionService.accordionFieldText( field, data );
      })
      .reject(_.isEmpty)
      .value()
      .join(" ");
  }

  /**
   * Form an optional header descpription
   * @param {String} identifierText  Document identifier text that user can input
   * @param {String} operationText   Operation description
   * @param {String} accordionText   Text extracted from document data
   */
  function headerDescription(identifierText, operationText, accordionText) {
    var isEmpty = !identifierText && !operationText && !accordionText;
    if (!isEmpty) {
      // accordionText handles its init string (and other separators)
      // with accordion-fields in schema.
      var initStr = " - "; // not all are empty, so we separate description from titleLoc with initial '-'
      var identifierAndOperation = _.filter([identifierText, operationText]).join(": ");
      return identifierAndOperation
           ? initStr + _.filter([identifierAndOperation, accordionText]).join("")
           : accordionText;
    } else {
      return "";
    }
  }

  // Context help

  function makeGroupHelpTextSpan(schema) {
    var span = document.createElement("span");
    span.className = "group-help-text";

    var locKey = schema["group-help"];
    if (locKey) {
      span.innerHTML = loc(locKey);
    }

    return span;
  }

  function makeSectionHelpTextSpan(schema) {
    var span = document.createElement("span");
    span.className = "group-help-text";
    var locKey = schema.info["section-help"];
    if (locKey) {
      span.innerHTML = loc(locKey);
    }

    return span;
  }

  function findHelpElement(e) {
    var event = getEvent(e);
    var input$ = $(event.target);
    var help$ = input$.siblings(".form-help");
    if (!help$.length) {
      help$ = input$.parent().siblings(".form-help");
    }
    if (!help$.length) {
      return false;
    }
    return help$;
  }

  function findErrorElement(e) {
    var event = getEvent(e);
    var input$ = $(event.target);
    var error$ = input$.siblings(".errorPanel");
    if (!error$.length) {
      error$ = input$.parent().siblings(".errorPanel");
    }
    if (!error$.length) {
      return false;
    }
    return error$;
  }

  function showError(e) {
    var element = findErrorElement(e);
    if (element && element.children && !_.isEmpty(element.children())) {
      element.stop();
      element.fadeIn("slow").css("display", "block");
    }
  }

  function hideError(e) {
    var element = findErrorElement(e);
    if (element) {
      element.stop();
      element.fadeOut("slow").css("display", "none");
    }
  }

  function showHelp(e) {
    var element = findHelpElement(e);
    if (element) {
      element.stop();
      element.fadeIn("slow").css("display", "block");
      var st = $(window).scrollTop(); // Scroll Top
      var y = element.offset().top;
      if ((y - 80) < (st)) {
        $("html, body").animate({ scrollTop: y - 80 + "px" });
      }
    }
    showError(e);
  }

  function hideHelp(e) {
    var element = findHelpElement(e);
    if (element) {
      element.stop();
      element.fadeOut("slow").css("display", "none");
    }
    hideError(e);
  }


  // Create an ajax-loader-12.gif img element
  function loaderImg() {
    var img = document.createElement("img");
    img.src = "/lp-static/img/ajax-loader-12.gif";
    img.alt = "...";
    img.width = 12;
    img.height = 12;
    return img;
  }

  // Indicators
  function createIndicator(eventTarget, className) {
    className = className || "form-indicator";
    var parent$ = $(eventTarget.parentNode);
    parent$.find("." + className).remove();
    var indicator = document.createElement("span");
    var icon = document.createElement("span");
    var text = document.createElement("span");
    text.className = "text";
    icon.className = "icon";
    indicator.className = className;
    indicator.appendChild(text);
    indicator.appendChild(icon);
    parent$.append(indicator);
    return indicator;
  }

  function showIndicator(indicator, className, locKey) {
    var parent$ = $(indicator).closest("table");
    var i$ = $(indicator);

    if(parent$.length > 0) {
      // disable indicator text for table element
      i$.addClass(className).fadeIn(200);
    } else {
      i$.children(".text").text(loc(locKey));
      i$.addClass(className).fadeIn(200);
    }

    setTimeout(function () {
      i$.removeClass(className).fadeOut(200, function () {});
    }, 4000);
  }

  function setMaxLen(input, subSchema) {
    var maxLen = subSchema["max-len"] || LUPAPISTE.config.inputMaxLength;
    input.setAttribute("maxlength", maxLen);
  }

  return {
    SELECT_ONE_OF_GROUP_KEY: SELECT_ONE_OF_GROUP_KEY,
    accordionText: accordionText,
    headerDescription: headerDescription,
    makeGroupHelpTextSpan: makeGroupHelpTextSpan,
    makeSectionHelpTextSpan: makeSectionHelpTextSpan,
    showHelp: showHelp,
    hideHelp: hideHelp,
    loaderImg: loaderImg,
    createIndicator: createIndicator,
    showIndicator: showIndicator,
    setMaxLen: setMaxLen
  };

})();
