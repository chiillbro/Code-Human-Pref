import json


def make_changes(x_changes, path_prefix="./"):
    for change in x_changes:
        """change = {
            "command": "str_replace",
            "path": path to file,
            "old_str": old string to replace,
            "new_str": new string to replace with
        }"""

        # copy the changes to the corresponding file
        with open(path_prefix + change["path"], "r") as f:
            content = f.read()
            content = content.replace(change["old_str"], change["new_str"])
        with open(path_prefix + change["path"], "w") as f:
            f.write(content)


if __name__ == "__main__":
    which_change = input("Which model you want to change? ")
    turn = int(input("which turn? "))
    if which_change == "a":
        with open(f"./turn-{turn}/a_changes.json", "r") as f:
            x_changes = json.load(f)
            path_prefix = f"./turn-{turn}/a/"
    else:
        with open(f"./turn-{turn}/b_changes.json", "r") as f:
            x_changes = json.load(f)
            path_prefix = f"./turn-{turn}/b/"
    make_changes(x_changes, path_prefix)
