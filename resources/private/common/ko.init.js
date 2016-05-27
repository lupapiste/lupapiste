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

  ko.validation.init({
    insertMessages: true,
    decorateInputElement: true,
    errorElementClass: "err",
    errorMessageClass: "error-message",
    parseInputAttributes: true,
    messagesOnModified: true,
    messageTemplate: "error-template",
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

  function localized(fnName, element, valueAccessor) {
    var e$ = $(element);
    var fn = e$[fnName];
    var value = ko.utils.unwrapObservable(valueAccessor());
    if (value) {
      var v = loc(value);
      fn.call(e$, (v ? v : "$$EMPTY_LTEXT$$"));
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
        $(element).text( value.split( "" ).join( " "));
      }
    }
  };

  ko.bindingHandlers.feature = {
    update: function( element, valueAccessor) {
      var value = ko.utils.unwrapObservable( valueAccessor() ) ;
      if( features.enabled(value) ) {
        $(element).addClass( "feature" );
      } else {
        $(element).remove();
      }
    }
  };

  ko.bindingHandlers.lhtml = {
    update: _.partial(localized, "html")
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

  ko.bindingHandlers.size = {
    update: function(element, valueAccessor) {
      var v = ko.utils.unwrapObservable(valueAccessor());

      if (!v || v.length === 0) {
        $(element).text("");
        return;
      }

      var value = parseFloat(v);
      var unit = "B";

      if (value > 1200.0) {
        value = value / 1024.0;
        unit = "kB";
      }

      if (value > 1200.0) {
        value = value / 1024.0;
        unit = "MB";
      }
      if (value > 1200.0) {
        value = value / 1024.0;
        unit = "GB";
      }
      if (value > 1200.0) {
        value = value / 1024.0;
        unit = "TB"; // Future proof?
      }

      if (unit !== "B") {
        value = value.toFixed(1);
      }

      $(element).text(value + "\u00a0" + unit);
    }
  };

  // Makes <a> element a download link for given file.
  // The file must have id and filename properties.
  ko.bindingHandlers.download = {
    update: function( element, valueAccessor) {
      var file = ko.utils.unwrapObservable(valueAccessor());
      $(element).attr( "href", "/api/raw/download-attachment?attachment-id=" + file.id);
      $(element).text( file.filename);
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

  ko.bindingHandlers.saveIndicator = {
    init: function(element, valueAccessor, allBindingsAccessor) {
      var bindings = ko.utils.unwrapObservable(allBindingsAccessor());
      var text$ = $("<span>").addClass("text");
      $(element).addClass("form-indicator");
      $(element).append(text$);
      $(element).append($("<span>").addClass("icon"));
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

      ko.utils.registerEventHandler(element, "click", function() {
        value(!value());
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
      var value = ko.utils.unwrapObservable(valueAccessor());
      element.readOnly = value;
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
      var dateStr = value ? monthName(value) + moment(value).format(" YYYY"): "";
      $(element).html(dateStr);
    }
  };

  ko.observable.fn.increment = function () {
    this(this() + 1);
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

})(jQuery);
