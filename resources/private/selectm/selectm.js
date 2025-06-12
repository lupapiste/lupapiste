;(function($) {

  "use strict";

  function nop() {}

  function toCallback(f) {
    if (!f) { return nop; }
    if (!_.isFunction(f)) { throw "callback must be a function: " + f; }
    return f;
  }

  function localize(prefix, i, elem) {
    var e = $(elem),
        t = e.attr("data-loc");
    if (t) {
      var locci = prefix ? loc([t, prefix]) : loc(t);
      if (e.is("input")) {
        e.attr("placeholder", locci);
      } else {
        e.text(locci);
      }
    }
  }

  $.fn.selectm = function(template, prefix) {
    var self = {};

    self.data = [];
    self.visible = [];
    self.duplicatesAllowed = false;

    this.append((template || $("#selectm-template"))
      .children()
      .first()
      .clone()
      .find("*").each(_.partial(localize, prefix)).end());

    self.$filter = $("input", this);
    self.$source = $(".selectm-source", this);
    self.$target = $(".selectm-target", this);
    self.$add = $(".selectm-add", this);
    self.$remove = $(".selectm-remove", this);
    self.$ok = $(".selectm-ok", this);
    self.$cancel = $(".selectm-cancel", this);
    self.okCallback = nop;
    self.ok = function(f) { self.okCallback = toCallback(f); return self; };
    self.cancelCallback = nop;
    self.cancel = function(f) { self.cancelCallback = toCallback(f); return self; };
    self.allowDuplicates = function(d) { self.duplicatesAllowed = d; return self; };

    self.filterData = function(filterValue) {
      var f = _.trim(filterValue).toLowerCase();
      if (_.isBlank(f)) { return self.data; }
      var newData = [];
      _.each(self.data, function(group) {
        var options = _.filter(group[1], function(o) { return o.text.toLowerCase().indexOf(f) >= 0; });
        if (options.length > 0) { newData.push([group[0], options]); }
      });
      return newData;
    };

    self.updateFilter = function() {
      var newVisible = self.filterData(self.$filter.val());
      if (_.isEqual(self.visible, newVisible)) { return; }
      self.$source.empty();
      self.visible = newVisible;
      _.each(self.visible, function(group) {
        self.$source.append($("<optgroup>").attr("label", group[0]));
        _.each(group[1], function(option) {
          self.$source.append($("<option>").data("id", option.id).data("text", option.text).html("&nbsp;&nbsp;" + option.text));
        });
      });
      self.check();
    };

    self.getSelected = function() {
      var trimmed = [];
      $("option:selected", self.$source).each( function( i, selected ) {
        trimmed.push($(selected).data());
      });
      return trimmed;
    };

    self.inTarget    = function(id) {
      return $("option", self.$target)
        .filter(function() { return _.isEqual($(this).data("id"), id); })
        .length;
    };

    self.canAdd      = function(ids) {
      if (_.isEmpty(ids)) {
        return false;
      }
      ids = _.isArray(ids) ? ids : [ids];
      var someFalsey = _.some(ids, function(id) {
        var isAddable = id && (self.duplicatesAllowed || !self.inTarget(id));
        return !isAddable;
      });
      return !someFalsey;
    };

    self.makeTarget  = function(d)  { return $("<option>").data("id", d.id).text(d.text); };

    self.addTarget   = function(d)  {
      if (d) {
        d = _.isArray(d) ? d : [d];
        if (self.canAdd(d)) {
          _.each(d, function(data) {
            if (data) {
              self.$target.append(self.makeTarget(data));
            }
          });
        }
      }
      return self;
    };

    self.add = function() {
      self.addTarget(self.getSelected());
      if (!self.duplicatesAllowed) {
        $("option", self.$source).each(function() {
          var e = $(this);
          e.prop("selected", false);
        });
      }
      self.check();
    };
    self.remove = function() { $("option:selected", self.$target).remove(); self.check(); };

    self.check = function() {
      self.$add.prop("disabled", !self.canAdd(self.getSelected()));
      self.$remove.prop("disabled", $("option:selected", self.$target).length === 0);
      if (!self.duplicatesAllowed) {
        $("option", self.$source).each(function() {
          var e = $(this);
          e.prop("disabled", self.inTarget(e.data("id")));
        });
      }
    };

    //
    // Register event handlers:
    //

    self.$filter.on("keyup",self.updateFilter);

    self.$source
      .on("keydown",function(e) { if (e.keyCode === 13) { self.add(); }})
      .on("dblclick", self.add)
      .on("change focus blur", self.check);

    self.$target
      .on("keydown",function(e) { if (e.keyCode === 13) { self.remove(); }})
      .on("dblclick", self.remove)
      .on("change focus blur", self.check);

    self.$add.on("click",self.add);
    self.$remove.on("click",self.remove);
    self.$cancel.on("click", function() { self.cancelCallback(); });
    self.$ok.on("click", function() { self.okCallback(
        _.map($("option", self.$target),
            function(e) { return $(e).data("id"); })); });

    //
    // Reset:
    //

    self.reset = function(sourceData, targetData) {
      self.visible = null;
      self.data = sourceData;
      self.$source.empty();
      self.$target.empty();
      _(targetData).map(self.addTarget).value();
      self.$filter.val("");
      self.updateFilter();
      return self;
    };

    self.reset([]);

    return self;
  };

})(jQuery);
