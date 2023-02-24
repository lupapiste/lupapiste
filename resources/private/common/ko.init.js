;(function($) {
  "use strict";

  ko.utils.domNodeDisposal.cleanExternalData = function () {
    // Do nothing. Now any jQuery data associated with elements will
    // not be cleaned up when the elements are removed from the DOM.
    // If jQuery events are bind, make sure to clean jQuery data in component disposal.
    // See:
    //   http://knockoutjs.com/documentation/custom-bindings-disposal.html#overriding-the-clean-up-of-external-data
    //   https://github.com/knockout/knockout/blob/8decc433942d7413b47768e0f45c304e8f15aa09/src/utils.domNodeDisposal.js#L88
    //   https://github.com/jquery/jquery/blob/1.12-stable/src/manipulation.js#L350
  };

  //
  // initialize Knockout validation
  //

  // We do not want automatic messages inserted into DOM.
  ko.validation.init({
    insertMessages: false,
    decorateInputElement: false,
    // errorElementClass: "err",
    // errorMessageClass: "error-message",
    parseInputAttributes: false,
    messagesOnModified: true,
    // messageTemplate: "error-template",
    registerExtenders: true
  });

  // As of 2013-03-25, the following keys are missing:
  // ["min", "max", "pattern", "step", "date", "dateISO", "digit", "phoneUS", "notEqual", "unique"];
  ko.validation.localize(loc.getErrorMessages());

  ko.validation.rules.validPassword = {
    validator: function (val) {
      return val && util.isValidPassword(val);
    },
    message: loc("error.password.minlength")
  };

  ko.validation.rules.y = {
    validator: function(y) {
      return _.isBlank(y) || util.isValidY(y);
    },
    message: loc("error.invalidY")
  };

  ko.validation.rules.ovt = {
    validator: function(ovt) {
      return _.isBlank(ovt) || util.isValidOVT(ovt);
    },
    message: loc("error.invalidOVT")
  };

  ko.validation.rules.personId = {
    validator: function(personId) {
      return _.isBlank(personId) || util.isValidPersonId(personId);
    },
    message: loc("error.illegal-hetu")
  };

  ko.validation.rules.usernameAsync = {
    async: true,
    validator: _.debounce(function(val, params, cb) {
      if (!params) {
        // don't validate
        return cb(true);
      }
      ajax.query("email-in-use", {email: val})
        .success(function() { cb(false); })
        .error(function() { cb(true); })
        .call();
    }, 500),
    message: loc("email-in-use")
  };

  ko.validation.rules.match = {
    validator: function(value1, value2) {
      return value1 === value2;
    },
    message: ""
  };

  ko.validation.rules.beforeThan = {
    validator: function(date, otherDate) {
      if (_.isDate(date) && _.isDate(otherDate)) {
        return date.getTime() <= otherDate.getTime();
      }
      return true;
    },
    message: loc("bulletin.beforeThan")
  };

  ko.validation.rules.afterThan = {
    validator: function(date, otherDate) {
      if (_.isDate(date) && _.isDate(otherDate)) {
        return date.getTime() >= otherDate.getTime();
      }
      return true;
    },
    message: loc("bulletin.afterThan")
  };

  /*
   * Determines if a field is required or not based on a function or value
   * Parameter: boolean function, or boolean value
   * Example
   *
   * viewModel = {
   *   var vm = this;
   *   vm.isRequired = ko.observable(false);
   *   vm.requiredField = ko.observable().extend({ conditional_required: vm.isRequired});
   * }
   *
   * Source: https://github.com/Knockout-Contrib/Knockout-Validation/wiki/User-Contributed-Rules
  */
  ko.validation.rules.conditional_required = {
    validator: function (val, condition) {
      var required = false;
      if (typeof condition === "function") {
        required = condition();
      } else {
        required = condition;
      }

      if (required) {
        return !(val === undefined || val === null || val.length === 0);
      } else {
        return true;
      }
    },
    message: ko.validation.rules.required.message
  };

  ko.validation.registerExtenders();

  ko.bindingHandlers.dateString = {
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var dateStr = value ? moment(value).format("D.M.YYYY") : "";
      $(element).text(dateStr);
    }
  };

  ko.bindingHandlers.dateTimeString = {
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var dateStr = value ? moment(value).format("D.M.YYYY HH:mm") : "";
      $(element).text(dateStr);
    }
  };

  ko.bindingHandlers.timeString = {
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var dateStr = value ? moment(value).format("HH:mm") : "";
      $(element).text(dateStr);
    }
  };

  function localized(fnName, element, valueAccessor, allBindings) {
    var e$ = $(element);
    var fn = e$[fnName];
    var value = ko.utils.unwrapObservable(valueAccessor());
    var locArguments = allBindings.get("locArgs");
    if (value) {
      var v = loc.apply(null, _.concat([value], locArguments));
      fn.call(e$, (_.isString( v ) ? v : "$$EMPTY_LTEXT$$"));
      if (v) {
        e$.removeClass("ltext-error");
      } else {
        e$.addClass("ltext-error");
      }
    } else {
      // value is null or undefined, show it as empty string. Note that this
      // does not mean that the localization would be missing. It's just that
      // the actual value to use for localization is not available at this time.
      fn.call(e$, "");
      e$.removeClass("ltext-error");
    }
  }

  ko.bindingHandlers.ltext = {
    update: _.partial(localized, "text")
  };

  // hello.id -> T e r v e
  // Used typically with vertical buttons.
  ko.bindingHandlers.lspaced = {
    update: function( element, valueAccessor) {
      var value = loc( ko.utils.unwrapObservable( valueAccessor() ) );
      if( value ) {
        $(element).html( value.split( "" ).join( " ").replace("   "," &nbsp; "));
      }
    }
  };

  // Hide the inactive feature and emphasize an active one.
  // Empty feature binding is safely ignored.
  ko.bindingHandlers.feature = {
    update: function( element, valueAccessor) {
      var value = ko.utils.unwrapObservable( valueAccessor() );
      if( value ) {
        if( features.enabled(value) ) {
          $(element).addClass( "feature" );
        }
        else {
          $(element).remove();
        }
      }
    }
  };

  ko.bindingHandlers.testId = {
    update: function( element, valueAccessor) {
      var value = ko.utils.unwrapObservable( valueAccessor() ) ;
      $(element).attr( "data-test-id", value );
    }
  };

  ko.bindingHandlers.lhtml = {
    update: _.partial(localized, "html")
  };

  function localizedAttribute( attribute, element, valueAccessor ) {
    var value = ko.unwrap( valueAccessor() );
    if( value ) {
      $(element).attr( attribute, loc( value ));
    }
  }

  ko.bindingHandlers.lplaceholder = {
    update: _.partial( localizedAttribute, "placeholder" )
  };

  ko.bindingHandlers.ltitle = {
    update: _.partial( localizedAttribute, "title" )
  };

  ko.bindingHandlers.fullName = {
    // The originally bound object should be a regular object and
    // contain firstName and lastName observables as properties.
    // However, there could be 'legacy' code that is initially
    // an empty observable. The latter are ignored in binding init.
    init: function( element, valueAccessor ) {
      if( !ko.isObservable( valueAccessor)) {
        // Unwrapping the observable properties creates dependency.
        // Thus, the property changes will trigger update (see below).
        ko.toJS( valueAccessor() );
      }
    },
    update: function(element, valueAccessor) {
      var value = ko.toJS(valueAccessor()) || {};
      $(element).text(_.filter([value.lastName, value.firstName]).join("\u00a0"));
    }
  };

  // First name Last name
  ko.bindingHandlers.firstLastName = {
    update: function( element, valueAccessor ) {
      var v = ko.utils.unwrapObservable( valueAccessor());
      if( v ) {
        $(element).text( v.firstName + "\u00a0" + v.lastName );
      }
    }
  };

  ko.bindingHandlers.size = {
    update: function(element, valueAccessor) {
      $(element).text( util.sizeString(ko.utils.unwrapObservable(valueAccessor())));
    }
  };

  // Makes <a> element a download link for given file.
  // The file must have id and filename properties.
  ko.bindingHandlers.download = {
    update: function( element, valueAccessor) {
      var file = ko.utils.unwrapObservable(valueAccessor());
      $(element).attr( "href", "/api/raw/download-attachment?file-id=" + file.fileId + "&id=" + lupapisteApp.models.application.id());
      $(element).text( file.filename);
    }
  };

  var latestVersionTemplate =
        _.template( "<a href='/api/raw/latest-attachment-version?download=true&attachment-id"
                    + "=<%- attachmentId %>'><%- filename %></a><br>"
                    + "<i class='fileinfo'><%- contentText %> <%- sizeText %></i>");

  // Fills the target element with:
  // <a href="attachment latest version file download url">filename</a><br>
  // <i>localized content type size string</i>
  ko.bindingHandlers.latestVersionDownload = {
    update: function( element, valueAccessor) {
      var attachmentId = util.getIn(valueAccessor(), ["attachmentId"]);
      var version = util.getIn(valueAccessor(), ["version"]);
      if( attachmentId && version ) {
        var data = ko.mapping.toJS( version );
        $(element).html( latestVersionTemplate( _.merge( data, {contentText: loc( data.contentType),
                                                                sizeText: util.sizeString( data.size ),
                                                                attachmentId: attachmentId})));
      }
    }
  };

  // Fully resolved attachmentType localized text.
  ko.bindingHandlers.attachmentType = {
    update: function( element, valueAccessor) {
      var v = ko.utils.unwrapObservable( valueAccessor());
      if( v ) {
        $(element).text( loc(["attachmentType",
                              util.getIn( v, ["type", "type-group"]),
                              util.getIn( v, ["type", "type-id"])]));
      }
    }
  };


  var downloadWithIcon = "<div class='download'>"
                       + "<a href='/api/raw/latest-attachment-version?download=true&attachment-id"
                       + "=<%- attachmentId %>'>"
                       + "<i class='lupicon-download btn-small'></i>"
                       + "<span><%- download %></span></a> (<%- sizeText %>)"
                       + "</div>";

  var downloadWithIconOnly  = "<span class='download'>"
                            + "<a data-bind='clickBubble: false, click: _.constant(true)'"
                            + " href='/api/raw/latest-attachment-version?download=true&attachment-id"
                            + "=<%- attachmentId %>'>"
                            + "<i class='lupicon-download btn-small'></i></a></span>";

  var viewWithDownloadTemplate =
        _.template(
          "<div class='view-with-download'><a target='_blank' "
            +"href='/api/raw/latest-attachment-version?attachment-id"
            + "=<%- attachmentId %>'><%- filename %></a><br>"
            + downloadWithIcon
            + "</div>");

  // Fills the target element with:
  // <a href="attachment latest version file view url" target="_blank">filename</a><br>
  // <div class="download">
  //   <a href="attachment latest version file download url">
  //     <i class="lupicon-download btn-small"></i>
  //     <span>Download file localization</span>
  //   </a>
  // (localized content size string)</div>
  ko.bindingHandlers.viewWithDownload = {
    update: function( element, valueAccessor) {
      var v = ko.utils.unwrapObservable( valueAccessor());
      if( v ) {
        var data = ko.mapping.toJS( v );
        $(element).html( viewWithDownloadTemplate( _.merge( data, {download: loc("download-file"),
                                                                   sizeText: util.sizeString( data.size )})));
      }
    }
  };

  var downloadWithIconTemplate = _.template( downloadWithIcon );
  var downloadWithIconOnlyTemplate = _.template( downloadWithIconOnly );

  // Only the download and icon part from viewWithDownload.
  ko.bindingHandlers.downloadWithIcon = {
    update: function( element, valueAccessor) {
      var v = ko.utils.unwrapObservable( valueAccessor());
      if( v ) {
        var data = ko.mapping.toJS( v );
        $(element).html( downloadWithIconTemplate( _.merge( data, {download: loc("download-file"),
                                                                   sizeText: util.sizeString( data.size )})));
      }
    }
  };

  // Only the icon part from viewWithDownload.
  ko.bindingHandlers.downloadWithIconOnly = {
    update: function( element, valueAccessor) {
      var v = ko.utils.unwrapObservable( valueAccessor());
      if( v ) {
        var data = ko.mapping.toJS( v );
        $(element).html( downloadWithIconOnlyTemplate( _.merge( data, {download: loc("download-file")})));
      }
    }
  };

  // Fills the A element with file view attributes and content. Links to the latest attachment version.
  // Note: works _only_ with A elements.
  // Value must contain attachmentId and filename properties.
  ko.bindingHandlers.fileLink = {
    update: function( element, valueAccessor) {
      var v = ko.utils.unwrapObservable( valueAccessor());
      if( v ) {
        var data = ko.mapping.toJS( v );
        $(element).attr( {target: "_blank",
                          href: "/api/raw/latest-attachment-version?attachment-id=" + data.attachmentId});
        $(element).text( data.filename );
      }
    }
  };


  var fileTemplate =
      _.template( "<a href='/api/raw/view-file?fileId"
                  + "=<%- fileId %>' target='_blank'><%- filename %></a><br>"
                  + "<span class='fileinfo'><%- contentText %> <%- sizeText %></span>");

  // Fills the target element with:
  // <a href="file view url" target="_blank">filename</a><br>
  // <span>localized content type size string</span>
  ko.bindingHandlers.file = {
    update: function( element, valueAccessor) {
      var v = ko.utils.unwrapObservable( valueAccessor());
      if( v ) {
        var data = ko.mapping.toJS( v );
        $(element).html( fileTemplate( _.merge( data, {contentText: loc( data.contentType),
                                                       sizeText: util.sizeString( data.size )})));
      }
    }
  };


  ko.bindingHandlers.version = {
    update: function(element, valueAccessor) {
      var verValue = ko.utils.unwrapObservable(valueAccessor());

      var version = "";
      if (verValue && (verValue.major || verValue.minor)) {
        if (typeof verValue.major === "function") {
          version = verValue.major() + "." + verValue.minor();
        } else {
          version = verValue.major + "." + verValue.minor;
        }
      }
      $(element).text(version);
    }
  };

  ko.bindingHandlers.propertyId = {
    update: function(element, valueAccessor) {
      var v = ko.utils.unwrapObservable(valueAccessor()),
          f = util.prop.toHumanFormat(v);
      $(element).text(f ? f : "");
    }
  };

  ko.bindingHandlers.datepicker = {
    init: function(element, valueAccessor, allBindingsAccessor) {
      //initialize datepicker with some optional options
      var options = allBindingsAccessor().datepickerOptions || {};
      $(element).datepicker(options);
      //handle the field changing
      ko.utils.registerEventHandler(element, "change", function () {
        var observable = valueAccessor();
        observable($(element).datepicker("getDate"));
      });

      //handle disposal (if KO removes by the template binding)
      ko.utils.domNodeDisposal.addDisposeCallback(element, function() {
        $(element).datepicker("destroy");
      });
      ko.bindingHandlers.validationCore.init(element, valueAccessor, allBindingsAccessor);
    },
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());

      //handle date data
      if (String(value).indexOf("/Date(") === 0) {
        value = new Date(parseInt(value.replace(/\/Date\((.*?)\)\//gi, "$1"), 10));
      }

      var current = $(element).datepicker("getDate");
      if (value - current !== 0) {
        $(element).datepicker("setDate", value);
      }
    }
  };

  // Binding value is an object or string observable.
  // Properties: [optional]
  // [cls]: Invalid value CSS class (default date-validator--invalid).
  // [isInvalid]: Observable that reflects the validation state.
  // [allowBlank]: Blank date is valid (default false)
  // If the value is just string it is interpreted as cls value.

  function dateInvalidParams( valueAccessor ) {
    var params = ko.unwrap( valueAccessor());
    params = _.isString( params )
         ? {cls: params}
         : params;
    params.cls = params.cls || "date-validator--invalid";
    params.isInvalid = params.isInvalid || ko.observable();
    return params;
  }

  function dateInvalidToggler( element, params) {
    var v = $(element).val();
    var valid = (params.allowBlank && _.isBlank( v ))
        || util.toMoment( v, "fi" );
    params.isInvalid( !valid );
    $(element).toggleClass( params.cls, params.isInvalid() );
  }

  ko.bindingHandlers.dateInvalidClass = {
    init: function(element, valueAccessor) {
      var params = dateInvalidParams( valueAccessor );
      $(element).on("change", _.partial( dateInvalidToggler, element, params) );
    },
    update: function( element, valueAccessor ) {
      dateInvalidToggler( element, dateInvalidParams( valueAccessor ));
    }
  };

  ko.bindingHandlers.saveIndicator = {
    init: function(element, valueAccessor, allBindingsAccessor) {
      var bindings = ko.utils.unwrapObservable(allBindingsAccessor());
      var text$ = $("<span>").addClass("text");
      $(element).addClass("form-indicator");
      $(element).append(text$);
      $(element).append($("<i>").addClass("lupicon-check positive"));
      if (bindings.label !== false) {
        text$.text(loc("form.saved"));
      }
    },
    update: function(element, valueAccessor, allBindingsAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var bindings = ko.utils.unwrapObservable(allBindingsAccessor());
      var name = bindings.name;
      var type = value ? (value.type ? ko.unwrap(value.type) : "saved") : undefined;
      var valueName = value ? ko.unwrap(value.name) : undefined;

      if (_.isString(value)) {
        valueName = value;
      }

      $(element).addClass("form-input-" + type);

      if (value && valueName === name || value && name === undefined) {
        $(element).fadeIn(200);
        setTimeout(function() {
          $(element).fadeOut(200, function() {
            $(element).removeClass("form-input-" + type);
          });
        }, 4000);
      }
    }
  };

  ko.bindingHandlers.transition = {
    init: function(element, valueAccessor, allBindingsAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var className = allBindingsAccessor()["class"];
      if (className) {
        $(element).toggleClass(className, value);
      }
    },
    update: function(element, valueAccessor, allBindingsAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var bindings = ko.utils.unwrapObservable(allBindingsAccessor());
      var className = bindings["class"];
      var type = bindings.type;
      var duration = bindings.duration || 100;

      if (LUPAPISTE.config.features.animations) {
        if (type) {
          $(element)[type + "Toggle"](1000);
        } else {
          $(element).toggleClass(className, value, duration);
        }
      } else {
        $(element).toggleClass(className, value);
      }
    }
  };

  ko.bindingHandlers.slider = {
    update: function(element, valueAccessor, allBindingsAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var bindings = ko.utils.unwrapObservable(allBindingsAccessor());
      var duration = bindings.duration || 100;
      var easing = bindings.easing || "swing";
      if (LUPAPISTE.config.features.animations) {
        if (value) {
          $(element).slideDown(duration, easing);
        } else {
          $(element).slideUp(duration, easing);
        }
      } else {
        $(element).toggle(value);
      }
    }
  };

  ko.bindingHandlers.fader = {
    update: function(element, valueAccessor, allBindingsAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var bindings = ko.utils.unwrapObservable(allBindingsAccessor());
      var duration = bindings.duration || 100;
      if (LUPAPISTE.config.features.animations) {
        if (value) {
          $(element).fadeIn({duration: duration, queue: false});
        } else {
          $(element).fadeOut({duration: duration, queue: false});
        }
      } else {
        $(element).toggle(value);
      }
    }
  };

  ko.bindingHandlers.fadeInOut = {
    update: function(element, valueAccessor, allBindingsAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var bindings = ko.utils.unwrapObservable(allBindingsAccessor());
      var duration = bindings.duration || 100;
      var delay = (bindings.delay || 1000) + duration;
      if (value) {
        if (LUPAPISTE.config.features.animations) {
          $(element).fadeIn({duration: duration, queue: false});
        } else {
          $(element).show();
        }
        _.delay(function() {
          if (LUPAPISTE.config.features.animations) {
            $(element).fadeOut({duration: duration, queue: false});
          } else {
            $(element).hide();
          }
        }, delay);
      }
    }
  };

  ko.bindingHandlers.drill = {
    init: function(element) {
      $(element).addClass("icon");
    },
    update: function(element, valueAccessor, allBindingsAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var bindings = ko.utils.unwrapObservable(allBindingsAccessor());
      var color = bindings.color || "white";
      if (value) {
        $(element).addClass("drill-down-" + color);
        $(element).removeClass("drill-right-" + color);
      } else {
        $(element).removeClass("drill-down-" + color);
        $(element).addClass("drill-right-" + color);
      }
    }
  };

  ko.bindingHandlers.toggleClick = {
    init: function(element, valueAccessor) {
      var value = valueAccessor();

      ko.utils.registerEventHandler(element, "click", function( e ) {
        value(!value());
        e.stopPropagation();
      });
    }
  };

  $.fn.applyBindings = function(model) {
    if (!this.length) {
      warn(this.selector + " didn't match any elements");
      return this;
    }
    if (this.length > 1) {
      warn("Apply bindings to " + this.length + " nodes");
    }
    _.each(this, _.partial(ko.applyBindings, model));
    return this;
  };

  ko.bindingHandlers.readonly = {
    update: function(element, valueAccessor) {
      element.readOnly = ko.utils.unwrapObservable(valueAccessor());
    }
  };

  ko.bindingHandlers.documentEvent = {
    init: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var keyupHandler = function(e) {
        if (e.keyCode === value.key) {
          if (value.keypress) {
            value.keypress();
          }
        }
      };

      $(document).on("keyup", keyupHandler);

      // http://www.knockmeout.net/2014/10/knockout-cleaning-up.html  - 3.
      ko.utils.domNodeDisposal.addDisposeCallback(element, function() {
        $(document).off("keyup", keyupHandler);
      });
    }
  };

  ko.bindingHandlers.titleWhenOverflow = {
    init: function(element, valueAccessor) {
      $(element).bind("mouseenter", function(){
        var $this = $(this);
        if(this.offsetWidth < this.scrollWidth) {
          $this.attr("title", ko.utils.unwrapObservable(valueAccessor()));
        }
      });
    }
  };

  function dayName(timeValue) {
    var names = $.datepicker.regional[loc.currentLanguage].dayNames;
    var day = moment(timeValue).day();
    return names[day];
  }

  function monthName(timeValue) {
    var names = $.datepicker.regional[loc.currentLanguage].monthNames;
    var month = moment(timeValue).month();
    return names[month];
  }

  function weekText(timeValue) {
    return "(" + loc("calendar.week") + moment(timeValue).format(" WW") + ")";
  }

  ko.bindingHandlers.calendarDayColumnHeader = {
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var dateStr = value ? moment(value).format("D ") + dayName(value) : "";
      $(element).html(dateStr);
    }
  };

  ko.bindingHandlers.calendarViewMonthText = {
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var dateStr = value ? monthName(value) + moment(value).format(" YYYY ") + weekText(value) : "";
      $(element).html(dateStr);
    }
  };

  ko.bindingHandlers.weekdayAndDate = {
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var dateStr = value ? dayName(value) + ", " + moment(value).format("D.M.YYYY") : "" ;
      $(element).html(dateStr);
    }
  };

  ko.observable.fn.increment = function (value) {
    this(this() + (value || 1));
  };

  ko.observable.fn.decrement = function (value) {
    this(this() - (value || 1));
  };

  ko.extenders.limited = function(target, optionObj) {
    var result = ko.pureComputed({
        read: target,
        write: function(newValue) {
          if (_.includes(optionObj.values, newValue)) {
            if (newValue !== target()) {
              target(newValue);
            }
          } else {
            target(optionObj.defaultValue);
          }
        }
    });
    result(target());
    return result;
  };

  ko.extenders.autoFetch = function(target, optionsObj) {
    // Observable extender that fetches content automatically when read and not initialized.
    // fetchFn should write result in autofetching observable array
    var fetching = false;
    var needsFetching = true;

    var autoFetched =  function(result) {
      if (arguments.length === 0) {
        if (needsFetching && !fetching) {
          fetching = true;
          optionsObj.fetchFn(optionsObj.fetchParams);
        }
        return target();
      } else {
        target(result);
        needsFetching = false;
        fetching = false;
      }
    };

    autoFetched.reset = function() {
      fetching = false;
      needsFetching = true;
    };

    return autoFetched;
  };

  ko.bindingHandlers.multilinePlaceholder = {
    init: function (element, valueAccessor) {
      var text = ko.utils.unwrapObservable(valueAccessor());

      setTimeout(function() {
        if (_.isEmpty($(element).val())) {
          $(element).val(text).addClass("placeholder-visible");
        }
      }, 0);

      $(element).focus(
        function () {
          if ($(element).val() === text) {
            $(element).val("").removeClass("placeholder-visible");
          }
        }).blur(function () {
          if (_.isBlank($(element).val())) {
            $(element).val(text).addClass("placeholder-visible");
          }
        });
    }
  };

  // Simple "focus trap" mechanism that helps to to keep keyboard
  // navigation (Tab, Shift-Tab) within dialog.

  function trapper( element, valueAccessor, shiftKey ) {
    var selector = ko.unwrap(valueAccessor());
    if( selector ) {
      $(element).on("keydown", function ( event )  {
        if( event.which === 9 && event.shiftKey === shiftKey ) {
          event.preventDefault();
          event.stopImmediatePropagation();
          $(selector).focus();
        }
      } );
      ko.utils.domNodeDisposal.addDisposeCallback(element, function() {
        $(element).off( "keydown" );
      });
    }
  }

  // Truthy value is jQuery selector for the next element. Falsey does nothing.
  ko.bindingHandlers.focusNext = {
    init: function(element, valueAccessor) {
      trapper( element, valueAccessor, false );
    }
  };

  // Truthy value is jQuery selector for the previous element. Falsey does nothing.
  ko.bindingHandlers.focusPrevious = {
    init: function(element, valueAccessor) {
      trapper( element, valueAccessor, true );
    }
  };

  // If the value is truthy, the focus will not leave the element. This is useful for dialogs with only OK button.
  ko.bindingHandlers.trapFocus = {
    init: function(element, valueAccessor) {
      if( ko.unwrap( valueAccessor() )) {
        $(element).on("keydown", function( event ) {
          if( event.which === 9) {
            event.preventDefault();
            event.stopImmediatePropagation();
          }
        });
      }
    }
  };

  // ARIA and related

  function toggleAttribute( element, name, value, always ) {
    var v = ko.unwrap( value );
    if( v || always ) {
      $(element).attr( name, v );
    } else {
      $(element).removeAttr( name );
    }
    return v;
  }

  function boolAccess( valueAccessor ) {
    return Boolean( ko.unwrap( valueAccessor() ));
  }

  function toggleRequired( element, flag ) {
    var tag = $(element).get()[0].tagName;
    if(tag === "LABEL" ) {
      $(element).toggleClass( "required", flag );
    } else {
      if( _.includes( ["INPUT", "TEXTAREA", "SELECT"], tag )) {
        $(element).prop( "required", flag );
      }
      toggleAttribute( element, "aria-required", flag);
    }
  }

  // Toggles required based on truthiness of the binding value.
  ko.bindingHandlers.required = {
    update: function(element, valueAccessor) {
      toggleRequired( element, boolAccess( valueAccessor ));
    }
  };

  // Required if the value is blank.
  ko.bindingHandlers.valueRequired = {
    update: function(element, valueAccessor) {
      var v = ko.unwrap( valueAccessor() );
      toggleRequired( element, _.isBlank( v ));
    }
  };

  ko.bindingHandlers.ariaErrorMessage = {
    update: function(element, valueAccessor) {
      var v = toggleAttribute( element, "aria-errormessage", valueAccessor() );
      toggleAttribute( element, "aria-invalid", Boolean( v ));
    }
  };

  ko.bindingHandlers.ariaHidden = {
    update: function( element, valueAccessor ) {
      toggleAttribute( element, "aria-hidden", boolAccess( valueAccessor ));
    }
  };

  ko.bindingHandlers.ariaChecked = {
    update: function( element, valueAccessor ) {
      toggleAttribute( element, "aria-checked", boolAccess( valueAccessor ), true);
    }
  };

  ko.bindingHandlers.ariaSelected = {
    update: function( element, valueAccessor ) {
      toggleAttribute( element, "aria-selected", boolAccess( valueAccessor ), true);
    }
  };

  ko.bindingHandlers.ariaPressed = {
    update: function( element, valueAccessor ) {
      toggleAttribute( element, "aria-pressed", boolAccess( valueAccessor ), true);
    }
  };


  // aria-label without l10n (like text binding)
  ko.bindingHandlers.ariaText = {
    update: function( element, valueAccessor ) {
      toggleAttribute( element, "aria-label", valueAccessor() );
    }
  };

  // aria-label with l10n support (like ltext binding)
  ko.bindingHandlers.ariaLtext = {
    update: _.partial( localizedAttribute, "aria-label" )
  };

  ko.bindingHandlers.ariaExpanded = {
    update: function( element, valueAccessor ) {
      toggleAttribute( element, "aria-expanded", boolAccess( valueAccessor ), true);
    }
  };

  ko.bindingHandlers.ariaInvalid = {
    update: function( element, valueAccessor ) {
      toggleAttribute( element, "aria-invalid", boolAccess( valueAccessor ));
    }
  };

  ko.bindingHandlers.id = {
    update: function( element, valueAccessor ) {
      toggleAttribute( element, "id", valueAccessor() );
    }
  };

  ko.bindingHandlers["for"] = {
    update: function( element, valueAccessor ) {
      toggleAttribute( element, "for", valueAccessor() );
    }
  };


  /**
   * Courtesy of https://github.com/calvinwoo/knockout-bind-react
   *
   * Usage
   * The react binding expects one value, an object containing the various options for the component:
   *
   * component: The desired React component
   * props: An object containing the props to be passed into the the React component
   *
   * <div data-bind="react: {component: ReactButton, props: {my: "props"}}"><div>
   */
  ko.bindingHandlers.react = {
    init: function (element) {
      ko.utils.domNodeDisposal.addDisposeCallback(element, function () {
        ReactDOM.unmountComponentAtNode(element);
      });

      return {
        controlsDescendantBindings: true
      };
    },

    update: function (element, valueAccessor) {
      var options = ko.unwrap(valueAccessor());

      if (options && options.component) {
        var componentInstance = ReactDOM.render(
            React.createElement(options.component, options.props),
            element
        );

        if (options.ref) {
          options.ref(componentInstance);
        }
      }
    }
  };

})(jQuery);
