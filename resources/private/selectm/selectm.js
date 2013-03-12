function loc(v) {
  return v;
}

function Selectm(element, onOk, onCancel) {
  var self = this;
  
  self.data = [];
  self.visible = [];

  self.$filter = $("input", element);
  self.$source = $(".source select", element);
  self.$target = $(".target select", element);
  self.$add = $(".add", element);
  self.$remove = $(".remove", element);
  self.$ok = $(".ok", element);
  self.$cancel = $(".cancel", element);

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
  self.$cancel.click(onCancel);
  self.$ok.click(function() { onOk(_.map($("option", self.$target), function(e) { return $(e).data("id"); })); });
  
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
 
  self.$filter.attr("placeholder", loc("filter"));
  self.$add.text(loc("add"));
  self.$remove.text(loc("remove"));
  self.$ok.text(loc("ok"));
  self.$cancel.text(loc("cancel"));
}

$(function() {
  var s = new Selectm($(".selectm"), function(ids) { console.log("OK:", ids);  }, function() { console.log("CANCEL"); });
  s.reset([["Fozzaa", ["foo1", "foo2"]],
           ["Bazzaa", ["bar1", "bar2", "bar3"]],
           ["Dozooz", ["doz1", "doz2", "doz3", "doz4"]],
           ["Ozzooo", ["ozz1", "ozz2", "ozz3", "ozz4", "ozz5"]]]);
});
