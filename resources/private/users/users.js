var users = (function() {
  "use strict";

  function toLoc(data, type, row) { return loc(data); }
  function toActive(data, type, row) { return loc(["users.data.enabled", data]); }
  
  function toOrgs(data, type, row) { return data ? data.join(", ") : ""; }
  
  function addOps(td, sData, oData, iRow, iCol) {
    var td = $(td).empty().text("");
    _.each(oData[5], function(op) {
      td.append($("<a>")
        .attr("href", "#")
        .attr("data-op", op)
        .text("[" + loc("users.op." + op) + "]"));
    });
  }
  
  var dataTableConfig = {
    bProcessing:      true, // don't hide this, it brakes layout.
    bServerSide:      true,
    sAjaxSource:      "",
    aoColumnDefs:     [{aTargets: [2], mRender: toLoc},
                       {aTargets: [3], mRender: toOrgs, bSortable: false},
                       {aTargets: [4], mRender: toActive},
                       {aTargets: [5], fnCreatedCell: addOps, bSortable: false}],
    aaSorting:        [[0, "desc"]],
    sDom:             "<t><<r><p><i><l>>", // <'table-filter' f>
    iDisplayLength:   10,
    oLanguage:        {sLengthMenu:   loc("applications.lengthMenu"),
                       sProcessing:   "&nbsp;",
                       sSearch:       loc("search") + ":",
                       sZeroRecords:  loc("applications.welcome"),
                       oPaginate:     {"sNext": loc("next"), "sPrevious": loc("previous")},
                       sInfo:         loc("applications.results"),
                       sInfoFiltered: ""}
  };

  function UsersModel(component, opts) {
    var self = this;
    
    self.component = component;
    
    self.filter = {
      role: ko.observable("foozzaa")
    };
    
    self.role$ = $("select.role", component);
    self.table$ = $("table", component);
    
    self.ops = function(user) {
      return user.enabled ? ["disable", "edit", "resetPassword"] : ["enable"];
    };
    
    self.userToRow = function(user) {
      return {id: user._id,
              0: user.email,
              1: user.firstName + " " + user.lastName,
              2: user.role,
              3: user.organizations ? user.organizations : [],
              4: user.enabled,
              5: self.ops(user),};
    };
    
    self.processResults = function(r) {
      console.log("r:", r);
      var data = r.data;
      return {aaData:               _.map(data.rows, self.userToRow),
              iTotalRecords:        data.total,
              iTotalDisplayRecords: data.display,
              sEcho:                data.echo};
    };
    
    self.fetch = function(source, data, callback) {
      var params = _(data)
        .concat(_.map(self.filter, function(v, k) { return {name: "filter-" + k, value: v()}; }))
        .reduce(function(m, p) { m[p.name] = p.value; return m; }, {});
      ajax
        .command("users-for-datatables")
        .json({params: params})
        .success(_.compose(callback, self.processResults))
        .call();
    };

    self.rowCreated = function(row, data) {
      $(row).attr("data-user-id", data.id);
    };

    var config = _.clone(dataTableConfig);
    config.fnServerData = self.fetch;
    config.fnCreatedRow = self.rowCreated;
    self.table$.dataTable(config);
    
    self.table$.click(function(e) {
      var target = $(e.target),
          op = target.attr("data-op"),
          id = target.parent().parent().attr("data-user-id");
      if (op && id) { console.log("click:", op, id); return false; }
      return true;
    });
    
    ko.applyBindings(self, component[0]);
    
    return self;
  }
  
  return {
    create: function(targetOrId, opts) {
      var component = $("#users-table .component").clone();
      var usersModel = new UsersModel(component, opts);
      (_.isString(targetOrId) ? $("#" + targetOrId) : targetOrId).append(component);
      return usersModel;
    }
  };

})();
