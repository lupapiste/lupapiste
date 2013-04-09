;(function($) {
  
  "use strict";
  
  $.fn.selectm = function(template) {
    var self = {};
    
    self.data = [];
    self.visible = [];
    self.duplicates = true;
  
    this.append(
      (template ? template : $("#selectm-template"))
        .children()
        .first()
        .clone()
        .find("input").attr("placeholder", loc("selectm.filter")).end()
        .find(".selectm-target-label").text(loc("selectm.target")).end()
        .find(".selectm-add").text(loc("selectm.add")).end()
        .find(".selectm-remove").text(loc("selectm.remove")).end()
        .find(".selectm-ok").text(loc("selectm.ok")).end()
        .find(".selectm-cancel").text(loc("selectm.cancel")).end());
     
    self.$filter = $("input", this);
    self.$source = $(".selectm-source", this);
    self.$target = $(".selectm-target", this);
    self.$add = $(".selectm-add", this);
    self.$remove = $(".selectm-remove", this);
    self.$ok = $(".selectm-ok", this);
    self.$cancel = $(".selectm-cancel", this);
    self.ok = function(f) { self.okCallback = f; return self; };
    self.cancel = function(f) { self.cancelCallback = f; return self; };
    self.allowDuplicates = function(d) { self.duplicates = d; return self; };
    
    self.filterData = function(filterValue) {
      var f = _.trim(filterValue).toLowerCase();
      if (_.isBlank(f)) return self.data;
      var newData = [];
      _.each(self.data, function(group) {
        var options = _.filter(group[1], function(o) { return o.text.toLowerCase().indexOf(f) >= 0; });
        if (options.length > 0) newData.push([group[0], options]);
      });
      return newData;
    };
    
    self.updateFilter = function() {
      var newVisible = self.filterData(self.$filter.val());
      if (_.isEqual(self.visible, newVisible)) return;
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
      return $("option:selected", self.$source).data() || {};
    };

    self.inTarget = function(id) { 
      return $("option", self.$target).filter(function() { return _.isEqual($(this).data("id"), id); }).length;
    };
    
    self.canAdd = function(id) {
      return id && (self.duplicates || !self.inTarget(id));
    };
    
    self.addTarget = function(d) {
      if (d && self.canAdd(d.id)) self.$target.append($("<option>").data("id", d.id).text(d.text));
      return self;
    };
    
    self.add = function() {
      self.addTarget(self.getSelected());
      self.check();
    };
  
    self.remove = function() {
      $("option:selected", self.$target).remove();
      self.check();
    };
  
    self.check = function() {
      self.$add.prop("disabled", !self.canAdd(self.getSelected().id));
      self.$remove.prop("disabled", $("option:selected", self.$target).length === 0);
      if (!self.duplicates) {
        $("option", self.$source).each(function() {
          var e = $(this);
          e.prop("disabled", self.inTarget(e.data("id")));
        });
      }
    };
    
    //
    // Register event handlers:
    //
    
    self.$filter.keyup(self.updateFilter);
  
    self.$source
      .keydown(function(e) { if (e.keyCode === 13) self.add(); })
      .dblclick(self.add)
      .on("change focus blur", self.check);
    
    self.$target
      .keydown(function(e) { if (e.keyCode === 13) self.remove(); })
      .dblclick(self.remove)
      .on("change focus blur", self.check);

    self.$add.click(self.add);
    self.$remove.click(self.remove);
    self.$cancel.click(function() { if (self.cancelCallback) self.cancelCallback(); });
    self.$ok.click(function() { if (self.okCallback) self.okCallback(_.map($("option", self.$target), function(e) { return $(e).data("id"); })); });
    
    //
    // Reset:
    //
    
    self.reset = function(sourceData, targetData) {
      self.visible = null;
      self.data = sourceData;
      self.$source.empty();
      self.$target.empty();
      _(targetData).each(self.addTarget);
      self.$filter.val("");
      self.updateFilter();
      self.check();
      return self;
    };
  
    self.reset([]);
     
    return self;
  };
  
})(jQuery);
