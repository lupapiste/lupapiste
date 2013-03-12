jQuery.fn.selectm = function() {
  var self = {};
  
  self.data = [];
  self.visible = [];

  this
    .addClass("selectm")
    .append($("<div>")
      .addClass("row")
      .append($("<input type='text'>").attr("placeholder", loc("selectm.filter"))))
    .append($("<div>")
      .addClass("row")
      .append($("<div>")
        .addClass("col")
        .addClass("source")
        .append($("<select>").attr("size", "2"))
        .append($("<button>").addClass("add").text(loc("selectm.add"))))
      .append($("<div>")
        .addClass("col")
        .addClass("target")
        .append($("<select>").attr("size", "2"))
        .append($("<button>").addClass("remove").text(loc("selectm.remove")))))
    .append($("<div>")
      .addClass("row")
      .append($("<button>").addClass("ok").text(loc("selectm.ok")))
      .append($("<button>").addClass("cancel").text(loc("selectm.cancel"))));
   
  self.$filter = $("input", this);
  self.$source = $(".source select", this);
  self.$target = $(".target select", this);
  self.$add = $(".add", this);
  self.$remove = $(".remove", this);
  self.$ok = $(".ok", this);
  self.$cancel = $(".cancel", this);
  self.ok = function(f) { self.okCallback = f; return self; };
  self.cancel = function(f) { self.cancelCallback = f; return self; };
  
  self.filterData = function(filterValue) {
    var f = _.trim(filterValue).toLowerCase();
    if (_.isBlank(f)) return self.data;
    var newData = [];
    _.each(self.data, function(group) {
      var options = _.filter(group[1], function(o) { return loc(o).toLowerCase().indexOf(f) >= 0; });
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
      self.$source.append($("<optgroup>").attr("label", loc(group[0])));
      _.each(group[1], function(option) {
        var name = loc(option);
        self.$source.append($("<option>").data("id", option).html("&nbsp;&nbsp;" + name));
      });
    });
    self.checkAdd();
  };
    
  self.add = function() {
    var id = $("option:selected", self.$source).data("id");
    if (id) self.$target.append($("<option>").data("id", id).text(loc(id)));
    self.checkOk();
  };

  self.remove = function() {
    $("option:selected", self.$target).remove();
    self.checkRemove();
    self.checkOk();
  };

  self.checkOk = function() { self.$ok.attr("disabled", $("option", self.$target).length === 0); };
  self.checkAdd = function() { self.$add.attr("disabled", $("option:selected", self.$source).length === 0); };
  self.checkRemove = function() { self.$remove.attr("disabled", $("option:selected", self.$target).length === 0); };

  //
  // Register event handlers:
  //
  
  self.$filter.keyup(self.updateFilter);

  self.$source
    .keydown(function(e) { if (e.keyCode === 13) self.add(); })
    .dblclick(self.add)
    .on("change focus blur", self.checkAdd);
  
  self.$target
    .keydown(function(e) { if (e.keyCode === 13) self.remove(); })
    .dblclick(self.remove)
    .on("change focus blur", self.checkRemove);
  

  self.$add.click(self.add);
  self.$remove.click(self.remove);
  self.$cancel.click(function() { if (self.cancelCallback) self.cancelCallback(); });
  self.$ok.click(function() { if (self.okCallback) self.okCallback(_.map($("option", self.$target), function(e) { return $(e).data("id"); })); });
  
  //
  // Reset:
  //
  
  self.reset = function(data) {
    self.$source.empty();
    self.$target.empty();
    self.data = data;
    self.$filter.val("");
    self.updateFilter();
    self.checkAdd();
    self.checkRemove();
    self.checkOk();
    return self;
  }

  self.reset([]);
   
  return self;
};
