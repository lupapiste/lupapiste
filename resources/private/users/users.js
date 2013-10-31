var users = (function() {
  "use strict";

  var template;
  
  function UsersModel(table) {
    var self = this;
    self.table = table;
  }
  
  $(function() {
    template = $("#users-table table").clone();
    $("th[data-loc]", template).each(function(i, e) {
      var th = $(e);
      th.text(loc(th.attr("data-loc")));
    });
  });
  
  return {
    create: function(elementOrId) {
      var table = template.clone();
      var div = _.isString(elementOrId) ? $("#" + elementOrId) : elementOrId;
      div.append(table);
      return new UsersModel(table);
    }
  };

})();
