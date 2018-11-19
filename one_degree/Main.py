from Environment import *
from RL import *

if __name__ == "__main__":
    q_table = build_q_table(TOTAL_STATES, ACTIONS)
    print("begin:")
    for i in range(0, MAX_EPISODES):
        total_step = 0
        state = 0
        while True:
            update_env(i, total_step, state)
            if state == TOTAL_STATES - 1:
                break
            action = choose_action(state, q_table)
            next_state, reward = get_feedback(state, action)
            do_reinforcement_learning(q_table, state, action, next_state, reward)
            state = next_state
            total_step += 1

    print("\nQ-Table:")
    print(q_table)
