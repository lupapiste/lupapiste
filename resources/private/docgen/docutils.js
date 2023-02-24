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
           ? initStr + _.filter([identifierAndOperation, accordionText]).join(" ")
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

  // function findErrorElement(e) {
  //   var event = getEvent(e);
  //   var input$ = $(event.target);
  //   var error$ = input$.siblings(".errorPanel");
  //   if (!error$.length) {
  //     error$ = input$.parent().siblings(".errorPanel");
  //   }
  //   if (!error$.length) {
  //     return false;
  //   }
  //   return error$;
  // }

  // function showError(e) {
  //   var element = findErrorElement(e);
  //   if (element && element.children && !_.isEmpty(element.children())) {
  //     element.stop();
  //     element.fadeIn("slow").css("display", "block");
  //   }
  // }

  // function hideError(e) {
  //   var element = findErrorElement(e);
  //   if (element) {
  //     element.stop();
  //     element.fadeOut("slow").css("display", "none");
  //   }
  // }

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
    //showError(e);
  }

  function hideHelp(e) {
    var element = findHelpElement(e);
    if (element) {
      element.stop();
      element.fadeOut("slow").css("display", "none");
    }
    //hideError(e);
  }

  // Binds given element to (earlier created) form help bubble, if one
  // exists (in the entrySpan). After binding:
  // - Elments aria-describedby refers to the bubble id.
  // - Help is shown when the element receives focus. Thus, non-focusable elements are not supported.
  // - Help is hidden when the component loses focus, the user
  //   clicks the bubble (the element retains focus) or the user presses Esc key.
  function bindHelp( element, entrySpan ) {
    var bubble = _.first($(entrySpan).find( ".form-help-bubble" ));
    if( bubble ) {
      $(element).attr( "aria-describedby", bubble.id );
      $(element).focus( function( e, doNotShow ) {
        if( !doNotShow) {
          showHelp( e );
        }
      });
      element.onblur = hideHelp;

      $(element).on("keydown", function( e ) {
        if( e.which === 27 ) { // Esc
          hideHelp( e );
        }
      });
      $(bubble).mousedown( function( e ) {
        e.target = element;
        hideHelp( e );
        e.preventDefault();
      });
    }
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

  var pingTemplate = _.template( "<div class='ping-wrapper ping--<%- style %>'>"
                                 + "<div class='like-btn'>"
                                 + "<span><%- text %></span><i class='lupicon-<%- icon %>'></i></span>"
                                 + "</div></div>");


  function showPing( eventTarget, style) {
    var ping$ = $(document.createElement("ping"));
    ping$.attr( "aria-hidden", true );
    ping$.html( pingTemplate( {style: style,
                               text: loc( LUPAPISTE.PING_DEFAULTS.texts[style]),
                               icon:  LUPAPISTE.PING_DEFAULTS.icons[style]}));
    var parent$ = $(eventTarget.parentNode);
    parent$.append( ping$ );
    _.delay(function() {ping$.remove();}, 1000 );
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

  // Validation warnings (warn and err validation
  // results). Initially, validation results are part of the model,
  // but later updated via command results. Thus, two separate
  // resolution functions are needed.

  function locPath( document, a, b, c, d, e, f ) {
    return util.locKeyFromDocPath(_([document.schemaI18name || _.get( document.schema, "name"),
                                     a, b, c, d, e, f])
                                  .flatten()
                                  .filter()
                                  .join("."));
  }

  function locKey( document, path, element ) {
    if( element.i18nkey ) {
      return element.i18nkey ;
    }
    return locPath( document, path,
                    _.last( path ) === element.name ? null : element.name,
                    _.includes( ["table", "group", "select"],
                                element.type )
                    ? "_group_label" : null);

  }

  function docId( document ) {
    return (document.docId || document.id);
  }

  function warningId( result ) {
    return _.join(["warn", _.get( result, "document.id" ),
                   _.replace( _.join( result.path, "-" ),
                              ".", "-" )],
                  "-" );
  }

  function updateWarnings( document, path, validationResults ) {
    if( _.get( validationResults, "0.document.id") === docId( document )) {
      return _( validationResults )
        .filter( function( r ) {
          // In the task (review) view the path is empty.
          return ( _.isEmpty( path) || _.isEqual(_.dropRight( r.path ), path))
            && _.includes(["err", "warn"], _.get( r, "result.0" ));
        })
        .map( function( r ) {
          return {label: loc(_.get( r, "element.locKey")),
                  error: "error." + _.get( r, "result.1"),
                  path: r.path,
                  id: warningId( r )};
        })
        .sortBy( "label" )
        .value();
    }
  }

  function initializeWarnings( document, schema, path ) {
    var warns = [];
    _.forEach( schema.body, function( field ) {
      var vr = _.get( document.model, _.concat( path, [field.name, "validationResult"]));
      if( _.first( vr ) === "warn" ) {
        var fieldPath = _.concat( path, [field.name]);
        warns.push( {label: loc( locKey( document, path, field) ),
                     error: "error." + _.last( vr ),
                     path: fieldPath,
                     id: warningId( {document: {id: document.docId},
                                     path: fieldPath} )});
      }
    });
    return _.sortBy( warns, "label" ) ;
  }

  return {
    SELECT_ONE_OF_GROUP_KEY: SELECT_ONE_OF_GROUP_KEY,
    accordionText: accordionText,
    headerDescription: headerDescription,
    makeGroupHelpTextSpan: makeGroupHelpTextSpan,
    makeSectionHelpTextSpan: makeSectionHelpTextSpan,
    bindHelp: bindHelp,
    createIndicator: createIndicator,
    showPing: showPing,
    showIndicator: showIndicator,
    setMaxLen: setMaxLen,
    warningId: warningId,
    locKey: locKey,
    updateWarnings: updateWarnings,
    initializeWarnings: initializeWarnings
  };

})();
