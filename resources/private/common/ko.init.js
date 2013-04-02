;(function($) {
  "use strict";

  //
  // initialize Knockout validation
  //

  ko.validation.init({
    insertMessages: true,
    decorateElement: true,
    errorMessageClass: "error-message",
    parseInputAttributes: true,
    messagesOnModified: true,
    messageTemplate: "error-template",
    registerExtenders: true
  });

  // As of 2013-03-25, the following keys are missing:
  // ["min", "max", "pattern", "step", "date", "dateISO", "digit", "phoneUS", "notEqual", "unique"];
  ko.validation.localize(loc.getErrorMessages());

  ko.validation.rules['passwordValid'] = {
      validator: function (val) {
          return val && util.passwordIsValid(val);
      },
      message: loc("error.password.minlength")
  };
  ko.validation.registerExtenders();
  
  ko.bindingHandlers.dateString = {
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      $(element).text(moment(value).format("D.M.YYYY"));
    }
  };

  ko.bindingHandlers.dateTimeString = {
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      $(element).text(moment(value).format("D.M.YYYY HH:mm"));
    }
  };

  ko.bindingHandlers.ltext = {
    update: function(element, valueAccessor) {
      var e$ = $(element);
      var value = ko.utils.unwrapObservable(valueAccessor());
      if (value) {
        var v = loc(value);
        e$.text(v ? v : "$$EMPTY_LTEXT$$");
        if (v) {
          e$.removeClass("ltext-error");
        } else {
          e$.addClass("ltext-error");
        }
      } else {
        // value is null or undefined, show it as empty string. Note that this
        // does not mean that the localization would be missing. It's just that
        // the actual value to use for localization is not available at this time.
        e$.text("").removeClass("ltext-error");
      }
    }
  };

  ko.bindingHandlers.fullName = {
    update: function(element, valueAccessor) {
      var value = ko.utils.unwrapObservable(valueAccessor());
      var fullName = "";
      if (value) {
        if (value.firstName) { fullName = _.isFunction(value.firstName) ? value.firstName() : value.firstName; }
        if (value.firstName && value.lastName) { fullName += "\u00a0"; }
        if (value.lastName) { fullName += _.isFunction(value.lastName) ? value.lastName() : value.lastName; }
      }
      $(element).text(fullName);
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

      $(element).text(value + " " + unit);
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

  $.fn.applyBindings = function(model) {
    _.each(this, _.partial(ko.applyBindings, model));
    return this;
  };

})(jQuery);
